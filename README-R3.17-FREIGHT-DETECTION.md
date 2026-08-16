NVU GTO R3.17 - FREIGHT DETECTION HOTFIX

This transfer package intentionally excludes node_modules and Android build artifacts/APKs to keep the archive small.

Corrected GTO detection sources are in:
android/app/src/main/java/com/nvu/operacional/

Main corrected components:
- GtoFastVisualDetector.java
- GtoObserverService.java
- GtoAutoTripSync.java
- GtoObserverPlugin.java
- GtoSelectionCoordinator.java
- GtoResultVisualGate.java
- GtoProjectionPermissionActivity.java
- MainActivity.java

Build on Windows:
1. npm install
2. npm run build (or the project's existing build command)
3. npx cap sync android
4. cd android
5. gradlew assembleRelease

Do not reuse APKs/build outputs from the old R3.16 package.
