# DriveGuard – driver hazard detection (Android, Kotlin)

Live camera → on-device object detection (MediaPipe + EfficientDet-Lite, COCO) → rule-based hazards:
red / yellow / green traffic light, pedestrians & cyclists in or near your lane, stop signs,
too-short following distance and forward-collision risk. Alerts appear as a banner and are spoken.

## Setup
1. Open this folder in Android Studio (it creates the Gradle wrapper and syncs; bump library versions if it suggests).
2. Download the model and put it in `app/src/main/assets/`:
   https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite
   (For better range, use `efficientdet_lite2` from the same bucket and change `MODEL` in `HazardDetector.kt`.)
3. Run on a real phone, mount it in landscape on the dashboard, grant Camera + Location (GPS speed).

## How it works
- `HazardDetector` – detects person, bicycle, car, motorcycle, bus, truck, traffic light, stop sign.
- `HazardAnalyzer` – "ego lane" = centre 30–70% of the frame. Distance = pinhole estimate from box size and
  assumed real size (car 1.8 m wide, person 1.7 m tall); FOV is read from the camera. Time gap = distance / GPS speed.
  Traffic-light colour = HSV pixel vote inside the box, majority-voted over 7 frames.
- Thresholds live at the top of `HazardAnalyzer.kt`; cooldowns in `AlertManager.kt`.

## Limits
Prototype only, not a certified safety system. Monocular distances are rough; small/far lights, night glare and LED
flicker reduce accuracy; speed needs a GPS fix. Don't operate the phone while driving and follow local mounting laws.
