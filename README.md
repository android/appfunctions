# AppFunctions Samples

This repository contains Android sample code demonstrating the usage of [AppFunctions](https://developer.android.com/ai/appfunctions) to provide application functionality to system services and agents.

## Samples

### ChatApp

The **ChatApp** sample showcases how a communication app can provide its core features to the Android intelligence system.
It is a functional chat application built with [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Hilt](https://developer.android.com/training/dependency-injection/hilt-android).

- **Location**: [ChatApp](ChatApp/)
- **Features**: Sending messages, searching contacts, and initiating calls via AppFunctions.

## Setup Guide

Follow these steps to explore and run the samples:

1.  **Prerequisites**:
    - [Android Studio](https://developer.android.com/studio).
    - Android SDK 36+.
    - A device or emulator running Android 16 (API 36) or higher.
2.  **Clone the Repository**:
    ```bash
    git clone https://github.com/android/appfunctions.git
    ```
3.  **Open in Android Studio**:
    - Open Android Studio and select **Open**.
    - Navigate to the specific sample directory (e.g., [`ChatApp`](ChatApp/) and click **OK**.
4.  **Sync and Build**: Wait for the Gradle sync to complete and build the project (**Build > Make Project**).
5.  **Run**: Click **Run > Run 'app'** to deploy the sample to your device.

## Contributing

We'd love to accept your patches and contributions!

- **Contributor License Agreement (CLA)**: All contributors must sign the [Google CLA](https://cla.developers.google.com/).
- **Code Style**: We use [Spotless](https://github.com/diffplug/spotless) to maintain consistent formatting. Run `./gradlew spotlessApply` before committing.
- **Testing**: Ensure all new functionality is covered by unit or instrumentation tests.
- **Reviews**: All submissions are reviewed via GitHub Pull Requests.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more detailed information.

## Resources

- **[AppFunctions Developer Guide](https://developer.android.com/ai/appfunctions)**: Comprehensive overview of the AppFunctions API and how to integrate it into your own apps.
- **[AppFunctions Skills](https://github.com/android/skills/tree/main/device-ai/appfunctions)**: Specialized skills unlocking AI driven AppFunction development.

---

License: Apache 2.0. See [LICENSE](LICENSE) for details.
