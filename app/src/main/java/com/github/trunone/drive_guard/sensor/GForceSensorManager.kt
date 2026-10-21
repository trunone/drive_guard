package com.github.trunone.drive_guard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Manages accelerometer and gravity sensors to calculate driving g-forces.
 *
 * Guarantees orientation-independence for the Z-axis g-force measurement
 * by projecting total acceleration onto the Earth's gravity vector.
 */
class GForceSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _gForceData = MutableStateFlow(GForceData())
    val gForceData: StateFlow<GForceData> = _gForceData.asStateFlow()

    @Volatile private var gravityValues = FloatArray(3) { 0f }
    @Volatile private var hasGravitySensorFix = false
    @Volatile private var calibratedFwdRef: FloatArray? = null

    // Low-pass filtered values for smooth visual output
    private var smoothedGx = 0f
    private var smoothedGy = 0f
    private var smoothedGz = 1.0f

    private var peakXy = 0f
    private var peakZMax = 1.0f
    private var peakZMin = 1.0f

    private val trajectoryBuffer = ArrayDeque<GForcePoint>()
    private var lastPointTs = 0L

    private var isListening = false

    fun start() {
        if (isListening || sensorManager == null) return
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        isListening = true
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    fun calibrateForwardOrientation() {
        if (gravityValues[0] == 0f && gravityValues[1] == 0f && gravityValues[2] == 0f) return
        // Store current device Y-axis as forward reference direction
        calibratedFwdRef = floatArrayOf(0f, 1f, 0f)
    }

    fun resetPeaks() {
        peakXy = _gForceData.value.gXyMag
        peakZMax = _gForceData.value.gz
        peakZMin = _gForceData.value.gz
        trajectoryBuffer.clear()
        _gForceData.value = _gForceData.value.copy(
            peakXy = peakXy,
            peakZMax = peakZMax,
            peakZMin = peakZMin,
            trajectory = emptyList()
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravityValues[0] = event.values[0]
                gravityValues[1] = event.values[1]
                gravityValues[2] = event.values[2]
                hasGravitySensorFix = true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // If no hardware gravity sensor is active, estimate gravity vector (-a) using low-pass filter
                if (!hasGravitySensorFix) {
                    val alpha = 0.8f
                    gravityValues[0] = alpha * gravityValues[0] + (1 - alpha) * (-ax)
                    gravityValues[1] = alpha * gravityValues[1] + (1 - alpha) * (-ay)
                    gravityValues[2] = alpha * gravityValues[2] + (1 - alpha) * (-az)
                }

                val result = computeGForce(
                    ax = ax,
                    ay = ay,
                    az = az,
                    gx = gravityValues[0],
                    gy = gravityValues[1],
                    gz = gravityValues[2],
                    calibratedRef = calibratedFwdRef
                )

                // Exponential moving average filter for smooth display rendering
                val filterAlpha = 0.25f
                smoothedGx = (1 - filterAlpha) * smoothedGx + filterAlpha * result.gx
                smoothedGy = (1 - filterAlpha) * smoothedGy + filterAlpha * result.gy
                smoothedGz = (1 - filterAlpha) * smoothedGz + filterAlpha * result.gz

                val xyMag = sqrt(smoothedGx * smoothedGx + smoothedGy * smoothedGy)

                peakXy = max(peakXy, xyMag)
                peakZMax = max(peakZMax, smoothedGz)
                peakZMin = min(peakZMin, smoothedGz)

                val nowMs = SystemClock.elapsedRealtime()

                // Retain only points from the last 10 seconds (10,000 ms)
                while (trajectoryBuffer.isNotEmpty() && nowMs - trajectoryBuffer.first().timestampMs > 10_000L) {
                    trajectoryBuffer.removeFirst()
                }

                // Throttle trajectory sampling slightly (every ~50ms) for smooth 20Hz trajectory point stream
                if (nowMs - lastPointTs >= 50L) {
                    trajectoryBuffer.addLast(GForcePoint(smoothedGx, smoothedGy, nowMs))
                    lastPointTs = nowMs
                }

                _gForceData.value = GForceData(
                    gx = smoothedGx,
                    gy = smoothedGy,
                    gz = smoothedGz,
                    gXyMag = xyMag,
                    peakXy = peakXy,
                    peakZMax = peakZMax,
                    peakZMin = peakZMin,
                    trajectory = trajectoryBuffer.toList()
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val GRAVITY_EARTH = SensorManager.GRAVITY_EARTH // 9.80665 m/s^2

        /**
         * Calculates Z-axis (gravity direction) and horizontal (XY plane) g-forces
         * given acceleration and gravity vectors in device coordinates.
         *
         * @param ax Accelerometer X in m/s^2
         * @param ay Accelerometer Y in m/s^2
         * @param az Accelerometer Z in m/s^2
         * @param gx Gravity X in m/s^2
         * @param gy Gravity Y in m/s^2
         * @param gz Gravity Z in m/s^2
         * @param calibratedRef Optional user-calibrated forward reference vector
         * @return Pure [GForceData] snapshot before time-smoothing
         */
        fun computeGForce(
            ax: Float,
            ay: Float,
            az: Float,
            gx: Float,
            gy: Float,
            gz: Float,
            calibratedRef: FloatArray? = null
        ): GForceData {
            val gMag = sqrt(gx * gx + gy * gy + gz * gz)
            if (gMag < 0.1f) {
                // Fallback if gravity readings are invalid
                return GForceData(gx = 0f, gy = 0f, gz = 1.0f, gXyMag = 0f)
            }

            // Unit gravity vector pointing towards Earth center in device frame
            val ugx = gx / gMag
            val ugy = gy / gMag
            val ugz = gz / gMag

            // Unit vector in Earth UP direction (opposite to gravity)
            val uzx = -ugx
            val uzy = -ugy
            val uzz = -ugz

            // Z-axis acceleration along gravity direction: scalar magnitude
            val signedAZ = ax * uzx + ay * uzy + az * uzz
            val gzForce = abs(signedAZ) / GRAVITY_EARTH

            // Horizontal acceleration vector in XY plane perpendicular to gravity:
            // a_XY = a - a_Z * u_Z
            val aXyX = ax - signedAZ * uzx
            val aXyY = ay - signedAZ * uzy
            val aXyZ = az - signedAZ * uzz

            // Forward horizontal unit vector u_fwd
            // Default reference: device Y-axis (0, 1, 0) projected on horizontal plane
            var refX = calibratedRef?.get(0) ?: 0f
            var refY = calibratedRef?.get(1) ?: 1f
            var refZ = calibratedRef?.get(2) ?: 0f

            // Project reference vector onto horizontal plane: p_fwd = ref - (ref . u_Z) * u_Z
            val refDotUz = refX * uzx + refY * uzy + refZ * uzz
            var pFwdX = refX - refDotUz * uzx
            var pFwdY = refY - refDotUz * uzy
            var pFwdZ = refZ - refDotUz * uzz
            var pFwdMag = sqrt(pFwdX * pFwdX + pFwdY * pFwdY + pFwdZ * pFwdZ)

            // If device Y-axis is parallel to gravity (e.g. phone standing perfectly vertical), fallback to device Z-axis
            if (pFwdMag < 0.01f) {
                refZ = 1f
                val dot2 = refZ * uzz
                pFwdX = -dot2 * uzx
                pFwdY = -dot2 * uzy
                pFwdZ = 1f - dot2 * uzz
                pFwdMag = sqrt(pFwdX * pFwdX + pFwdY * pFwdY + pFwdZ * pFwdZ)
            }

            if (pFwdMag < 0.01f) {
                // Extreme singular case fallback
                val xyMag = sqrt(aXyX * aXyX + aXyY * aXyY + aXyZ * aXyZ) / GRAVITY_EARTH
                return GForceData(gx = 0f, gy = 0f, gz = gzForce, gXyMag = xyMag)
            }

            val uFwdX = pFwdX / pFwdMag
            val uFwdY = pFwdY / pFwdMag
            val uFwdZ = pFwdZ / pFwdMag

            // Lateral horizontal unit vector u_lat = u_fwd x u_Z (Right direction)
            val uLatX = uFwdY * uzz - uFwdZ * uzy
            val uLatY = uFwdZ * uzx - uFwdX * uzz
            val uLatZ = uFwdX * uzy - uFwdY * uzx

            // Project horizontal acceleration onto lateral and forward axes
            val gyForce = (aXyX * uFwdX + aXyY * uFwdY + aXyZ * uFwdZ) / GRAVITY_EARTH
            val gxForce = (aXyX * uLatX + aXyY * uLatY + aXyZ * uLatZ) / GRAVITY_EARTH

            val xyMag = sqrt(gxForce * gxForce + gyForce * gyForce)

            return GForceData(
                gx = gxForce,
                gy = gyForce,
                gz = gzForce,
                gXyMag = xyMag
            )
        }
    }
}
