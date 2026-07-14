# AppFunctions Samples

This repository contains Android sample code demonstrating the usage of [AppFunctions](https://developer.android.com/ai/appfunctions) to provide application functionality to system services and agents.

## Projects

This repository contains two primary projects. An API sample and a testing agent.

### 1. ChatApp sample

The **ChatApp** sample showcases how a communication app can provide its core features to the Android intelligence system. It serves as an educational reference for implementing AppFunctions.
It is a functional chat application built with [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Hilt](https://developer.android.com/training/dependency-injection/hilt-android), demonstrating AppFunctions usage across both mobile and Wear OS form factors.

- **Location**: [ChatApp](ChatApp/)
- **Structure**:
    - `app`: Main mobile chat application.
    - `wear`: Wear OS companion chat application.
    - `shared`: Common data repositories, ViewModels, and AppFunction service definitions.
- **Features**: Sending messages, searching contacts, and initiating calls via AppFunctions.

### 2. AppFunctions Testing Agent

The **AppFunctions Testing Agent** is a testing and debugging tool for Android AppFunctions. It is designed to help developers verify their integrations, troubleshoot execution issues, and evaluate AppFunctions using both manual deterministic inputs and an LLM-based agent.

- **Location**: [agent](agent/)
- **Documentation**: See the [Agent README](agent/README.md) for full building, running, and testing instructions.
- **Features**: Privileged AppFunction execution, manual debugging, test automation, and LLM agent evaluations.

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
    - Navigate to the specific project directory (e.g., [`ChatApp`](ChatApp/) or [`agent`](agent/)) and click **OK**.
4.  **Sync and Build**: Wait for the Gradle sync to complete and build the project (**Build > Make Project**).
5.  **Run**: For `ChatApp`, click **Run > Run 'app'** to deploy the mobile sample to your phone/emulator, or **Run > Run 'wear'** to deploy the companion app to a Wear OS device/emulator. For `agent`, refer to the [Agent README](agent/README.md) for privileged launch instructions (`./run_privileged.sh`).

## Contributing

We'd love to accept your patches and contributions!

- **Contributor License Agreement (CLA)**: All contributors must sign the [Google CLA](https://cla.developers.google.com/).
- **Code Style**: We use [Spotless](https://github.com/diffplug/spotless) to maintain consistent formatting. Because this repository contains separate Gradle projects for each sample, run `./agent/gradlew -p agent spotlessApply && ./ChatApp/gradlew -p ChatApp spotlessApply` from the repository root (or `./gradlew spotlessApply` inside a specific sample directory) before committing.
- **Testing**: Ensure all new functionality is covered by unit or instrumentation tests.
- **Reviews**: All submissions are reviewed via GitHub Pull Requests.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more detailed information.

## Resources

- **[AppFunctions Developer Guide](https://developer.android.com/ai/appfunctions)**: Comprehensive overview of the AppFunctions API and how to integrate it into your own apps.
- **[AppFunctions Skills](https://github.com/android/skills/tree/main/device-ai/appfunctions)**: Specialized skills unlocking AI driven AppFunction development.

---

License: Apache 2.0. See [LICENSE](LICENSE) for details.
