#!/bin/bash

set -euo pipefail # bash sanity checks, exit with error if a command fails
cd "$(dirname "${0}")" # go to the directory of this script

tag=${1:-local}
# when updating this repository, also update
# - hivemq-edge-adapter-sdk/
# - hivemq-edge-extension-sdk/
# to the same version
echo "Building hivemq-edge from source with modules, tagging as acriedweeushd.azurecr.io/hivemq/hivemq-edge:2026.8-mqtt3-${tag}"

cd ..
./gradlew -Dorg.gradle.java.home=/home/patrickd/.jdks/corretto-26.0.1 loadOciImage

docker build -f docker/Dockerfile . -t "acriedweeushd.azurecr.io/hivemq/hivemq-edge:2026.8-mqtt3-${tag}"
