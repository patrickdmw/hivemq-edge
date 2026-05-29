#!/bin/bash

set -euo pipefail # bash sanity checks, exit with error if a command fails
cd "$(dirname "${0}")" # go to the directory of this script

tag=${1:-local}

echo "Building hivemq-edge from source with modules, tagging as acriedweeushd.azurecr.io/hivemq/hivemq-edge:2026.8-mqtt3-${tag}"
echo "Then pushing the docker image to the registry."

./build.sh "${tag}"

docker push "acriedweeushd.azurecr.io/hivemq/hivemq-edge:2026.8-mqtt3-${tag}"
