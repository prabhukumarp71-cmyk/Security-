# Security Cam

A continuous background security camera application built for Android.

## Features
* **Continuous Capture:** Takes photos at a configurable interval while in the background.
* **Motion Detection:** Analyzes frames to detect motion and saves photos.
* **Background Execution:** Runs as a Foreground Service, surviving screen-off and other app usage.
* **Storage Management:** Automatically cleans up photos older than a specified retention period.
* **Screen-off Resilience:** Acquires a partial wake lock and prompts the user to disable battery optimizations.

## Build Instructions
1. Clone the repository or download the project.
2. Open the project in Android Studio.
3. Ensure you have the Android SDK 36 (compileSdk 36) installed.
4. The project uses Gradle (Kotlin DSL). Sync the project.
5. Build the APK or run directly on a physical device.

## Why the Persistent Notification Must Stay Visible
Android requires any application running a foreground service (especially one accessing the camera or microphone) to display a persistent notification. This is a privacy and security feature enforced by the Android OS to ensure users are always aware when an app is actively using sensitive hardware in the background. If the notification is hidden or the service is not started correctly, the system will kill the app within a minute.

## Android Doze Limitations
When an Android device is unplugged and its screen is turned off for an extended period, it enters "Doze" mode. Doze mode severely restricts background CPU and network activity to save battery. While this app attempts to acquire a `PARTIAL_WAKE_LOCK`, Android 10+ devices (including the Moto Edge 50 Fusion) may still throttle background camera access and CPU usage. 

To ensure the most reliable screen-off capture:
1. **Disable Battery Optimization:** Allow the app to ignore battery optimizations (use the button in the app's UI).
2. **Keep the Device Plugged In:** Android generally suspends Doze mode when the device is charging.

## Moto Edge 50 Fusion Testing Steps
The Motorola Moto Edge 50 Fusion ships with a clean Android build, but Motorola's stock battery manager can be aggressive.
1. Install the app on the device.
2. Open the app and grant Camera and Notification permissions.
3. Click "Disable Optimization" on the main screen. You will be redirected to the App Info settings.
4. Navigate to **Battery** -> select **Unrestricted**. This step is critical on Motorola devices.
5. Start the service, lock the screen, and verify photos are saved correctly in the `Pictures/SecurityCam` directory.
