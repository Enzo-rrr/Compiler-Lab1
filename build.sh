#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
chmod +x gradlew
./gradlew --no-daemon installDist
chmod +x build/install/compiler/bin/compiler
