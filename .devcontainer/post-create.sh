#!/usr/bin/env bash
set -euo pipefail

if [ -f .gitmodules ]; then
  git submodule update --init --recursive
fi

adb version >/dev/null

if [ -d /dev/bus/usb ]; then
  echo "USB bus is mounted into the devcontainer."
else
  echo "USB bus is not mounted. Rebuild the devcontainer before using adb over USB." >&2
fi

