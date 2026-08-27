Build APK:
  ./gradlew assembleStore_googleDebug

Run instrumented tests with a connected device or emulator:
  ./gradlew connectedNoneDebugAndroidTest

Artifacts are written into the bind-mounted repository on the host, for example:
  app/build/outputs/

This devcontainer reuses the repository build image definition from:
  scripts/Dockerfile
