#!/usr/bin/env bash
set -xeuo pipefail

# This is surely brittle and will need to change as cmake updates.
echo y | sdkmanager "cmake;3.6.4111459"

./gradlew :psicashlib:clean
./gradlew :psicashlib:assembleDebug :psicashlib:assembleRelease
