#!/usr/bin/env bash
# Copyright 2022-2026 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Publishes locally-built Sleeper jars from scripts/jars/ to a chosen S3 bucket,
# optionally under a shared artefacts prefix (e.g. a release version or branch name).
# The resulting S3 layout matches what a Sleeper instance configured with
# sleeper.artefacts.mode=published expects.

set -e
unset CDPATH

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 <target-s3-bucket> <optional-artefacts-prefix>"
  echo
  echo "Example (tagged release):         $0 sleeper-releases v1.0.0"
  echo "Example (branch snapshot):        $0 sleeper-releases branches/feature-x"
  echo "Example (no prefix, bucket root): $0 sleeper-releases"
  exit 1
fi

THIS_DIR=$(cd "$(dirname "$0")" && pwd)
SCRIPTS_DIR=$(cd "${THIS_DIR}/.." && pwd)
JARS_DIR="${SCRIPTS_DIR}/jars"
VERSION=$(cat "${SCRIPTS_DIR}/templates/version.txt")

TARGET_BUCKET=$1
ARTEFACTS_PREFIX=${2:-}

java -cp "${JARS_DIR}/clients-${VERSION}-utility.jar" \
    sleeper.clients.deploy.jar.SyncJars \
    "${JARS_DIR}" "${TARGET_BUCKET}" "${ARTEFACTS_PREFIX}" false
