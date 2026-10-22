# DriveGuard – Real-Time Driver Hazard & Traffic Signal Detection

DriveGuard (`com.github.trunone.drive_guard`) is an Android application written in Kotlin designed for real-time driver assistance, hazard detection, and intelligent traffic signal monitoring.

## Key Features

- **Dual-Source Video Feed**: Supports standard device camera feeds via CameraX as well as live RTSP video streams.
- **Two-Stage Traffic Light Vision Pipeline**:
  - **Stage 1 Object Detection**: Uses `yolov8n_traffic_detector.tflite` for fast localized traffic signal bounding box detection.
  - **Stage 2 State Classification**: Uses `traffic_state_classifier.tflite` alongside temporal smoothing (`TemporalSmoothingDispatcher`) for accurate traffic light state classification (Red, Yellow, Green, Off, Unknown).
- **Driver Hazard Analysis**: Pinpoints objects (vehicles, pedestrians, cyclists) in the ego lane using pinhole monocular distance estimation, safe headway/time-to-collision (TTC) calculations, and configurable thresholds.
- **V2X & MQTT iVRI Ingestion**: Ingests real-time traffic signal streams via MQTT (`org.eclipse.paho`) parsing iVRI payloads, persisted locally via Room (`AppDatabase`).
- **G-Force Monitoring**: Real-time G-force sensor tracking (`GForceSensorManager`) with peak recording and orientation calibration rendered with Jetpack Compose (`GForceMeterCard`).
- **Interactive Compose HUD & Dashboard**: Real-time traffic signal status UI overlay (`TrafficLightDashboard`) and customizable alert audio/banner feedback (`AlertManager`).

## Setup & Assets

### Model Download Links
Model files can be downloaded from official project releases and model sites:
- **Official DriveGuard Releases Site**: [https://github.com/trunone/drive_guard/releases](https://github.com/trunone/drive_guard/releases)
- **Official TensorFlow Lite Website**: [https://www.tensorflow.org/lite/models](https://www.tensorflow.org/lite/models)
- **Official Ultralytics Website**: [https://ultralytics.com](https://ultralytics.com)

### Model Import Options
You can load the model files into DriveGuard using either method:
- **In-App Import (Runtime)**: Download `.tflite` model files to your device, tap **"Import Model (.tflite)"** on the top banner or in the Settings panel within the app, and select the `.tflite` file.
- **Build-Time Assets**: Place the downloaded model files directly into `app/src/main/assets/` before compiling.

### Getting Started
1. Open the project in Android Studio (Java 17 required).
2. Build and install on an Android device running Android 8.0 (API 26) or higher (`./gradlew assembleDebug`).
3. Grant Camera and Location permissions when prompted.

## Architecture Highlights

- **`com.github.trunone.drive_guard`**: Base package containing `MainActivity` HUD integration.
- **`vision/`**: Stage 1 YOLOv8 detector, Stage 2 traffic state classifier, and temporal smoothing dispatcher.
- **`sensor/` & `ui/`**: G-Force sensor manager, Jetpack Compose HUD dashboards, and bounding box overlays.
- **`mqtt/` & `data/`**: MQTT client manager for iVRI traffic signals, Room database (`AppDatabase`, `TrafficLightDao`), and repository pattern.
- **`service/`**: Foreground `LocationTrackingService` for GPS speed and region boundary evaluations.

## Testing & Build

Run unit tests via Gradle:
```bash
./gradlew test
```

Build the debug APK:
```bash
./gradlew assembleDebug
```

## Disclaimer

DriveGuard is a research and prototyping platform. Monocular distance estimates and vision classifications are subject to environmental conditions (glare, weather, night conditions). It is not a certified automotive safety system. Always stay attentive while driving.
