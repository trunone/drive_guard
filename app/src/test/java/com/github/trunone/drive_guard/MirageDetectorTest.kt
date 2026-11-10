package com.github.trunone.drive_guard

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.github.trunone.drive_guard.vision.MirageDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MirageDetectorTest {

    @Test
    fun testLabelMapping() {
        assertEquals("traffic light", MirageDetector.mapLabelToRelevantClass("traffic light"))
        assertEquals("traffic light", MirageDetector.mapLabelToRelevantClass("overhead traffic signal"))
        assertEquals("stop sign", MirageDetector.mapLabelToRelevantClass("octagonal red stop sign"))
        assertEquals("car", MirageDetector.mapLabelToRelevantClass("silver car"))
        assertEquals("car", MirageDetector.mapLabelToRelevantClass("blue sedan"))
        assertEquals("truck", MirageDetector.mapLabelToRelevantClass("white pickup truck"))
        assertEquals("bus", MirageDetector.mapLabelToRelevantClass("city bus"))
        assertEquals("motorcycle", MirageDetector.mapLabelToRelevantClass("motorbike"))
        assertEquals("person", MirageDetector.mapLabelToRelevantClass("pedestrian crossing"))
        assertEquals("bicycle", MirageDetector.mapLabelToRelevantClass("bicycle with basket"))
        assertNull(MirageDetector.mapLabelToRelevantClass("tree"))
        assertNull(MirageDetector.mapLabelToRelevantClass("building"))
    }

    @Test
    fun testParseDetections() {
        val mockJson = """
            {
              "objects": [
                {
                  "name": "car",
                  "score": 0.88,
                  "bbox": [100, 200, 300, 400]
                },
                {
                  "name": "traffic light",
                  "score": 0.92,
                  "bbox": [500, 50, 550, 150]
                },
                {
                  "name": "bench",
                  "score": 0.75,
                  "bbox": [10, 10, 50, 50]
                },
                {
                  "name": "person",
                  "score": 0.20,
                  "bbox": [50, 50, 100, 100]
                }
              ]
            }
        """.trimIndent()

        val imageW = 1000
        val imageH = 1000
        val dets = MirageDetector.parseDetections(mockJson, imageW, imageH, minConfidence = 0.35f)

        assertEquals(2, dets.size)

        val carDet = dets.firstOrNull { it.label == "car" }
        assertNotNull(carDet)
        assertEquals(100f, carDet!!.box.left, 0.1f)
        assertEquals(200f, carDet.box.top, 0.1f)
        assertEquals(300f, carDet.box.right, 0.1f)
        assertEquals(400f, carDet.box.bottom, 0.1f)
        assertEquals(0.88f, carDet.score, 0.01f)

        val tlDet = dets.firstOrNull { it.label == "traffic light" }
        assertNotNull(tlDet)
        assertEquals(500f, tlDet!!.box.left, 0.1f)
        assertEquals(50f, tlDet.box.top, 0.1f)
        assertEquals(550f, tlDet.box.right, 0.1f)
        assertEquals(150f, tlDet.box.bottom, 0.1f)
    }

    @Test
    fun testBitmapToRgbBytes() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val rgb = MirageDetector.bitmapToRgbBytes(bmp)
        assertEquals(10 * 10 * 3, rgb.size)
    }

    @Test
    fun testHazardDetectorInitializesWithMirageAsset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = HazardDetector(context)
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val dets = detector.detect(bmp)
        assertNotNull(dets)
        detector.close()
    }
}
