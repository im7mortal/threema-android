#!/usr/bin/env bash
set -euo pipefail

if [ -f .gitmodules ]; then
  git submodule update --init --recursive
fi
