#!/bin/sh
set -e
cd "$(dirname "$0")"
./gradlew --no-daemon clean build
printf '\nBuild completed. Output: %s/build/libs\n' "$PWD"
