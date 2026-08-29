Build APK:
  ./gradlew assembleStore_googleDebug

Run instrumented tests with a connected device or emulator:
  adb devices -l
  ./gradlew connectedNoneDebugAndroidTest

Artifacts are written into the bind-mounted repository on the host, for example:
  app/build/outputs/

USB-backed adb is routed into the container by:
  - bind-mounting /dev/bus/usb
  - allowing USB character devices with Docker device cgroup rule `c 189:* rmw`
  - running the adb server as root inside the container via the `adb` wrapper

Notes:
  - The adb server runs inside the container, not on the host.
  - If the host already has an adb server, stop it once with: `adb kill-server`
  - Rebuild/reopen the devcontainer after changing `devcontainer.json`.
  - If your phone does not appear, confirm that USB debugging is enabled and replug it.
  - Most Ubuntu hosts work without extra udev changes because adb runs as root inside the container.
    If your host applies stricter USB ACLs, add a vendor-specific udev rule on the host.

This devcontainer currently uses `.devcontainer/Dockerfile`.
