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
# Publishes a Sleeper release (jars + Docker images) to a shared S3 bucket and ECR
# registry so that many Sleeper instances can be deployed against these artefacts
# with sleeper.artefacts.mode=published.

set -e
unset CDPATH

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
  echo "Usage: $0 <target-s3-bucket> <target-ecr-uri> <artefacts-prefix> [<create-buildx-builder-true-or-false>]"
  echo
  echo "  target-s3-bucket:    bucket to publish jars into"
  echo "  target-ecr-uri:      full ECR URI including repository prefix, e.g."
  echo "                       111122223333.dkr.ecr.eu-west-2.amazonaws.com/sleeper"
  echo "  artefacts-prefix:    shared namespace for this release, e.g. v1.0.0 or branches/feature-x"
  echo
  echo "Example:"
  echo "  $0 sleeper-releases 111122223333.dkr.ecr.eu-west-2.amazonaws.com/sleeper v1.0.0"
  exit 1
fi

THIS_DIR=$(cd "$(dirname "$0")" && pwd)
SCRIPTS_DIR=$(cd "${THIS_DIR}/.." && pwd)
VERSION=$(cat "${SCRIPTS_DIR}/templates/version.txt")
TARGET_BUCKET=$1
TARGET_ECR=$2
ARTEFACTS_PREFIX=$3
CREATE_BUILDX=${4:-true}

# Parse the ECR URI into its components. Expected form:
#   <account>.dkr.ecr.<region>.<dns-suffix>/<repository-prefix>
ECR_URI_REGEX='^([0-9]+)\.dkr\.ecr\.([a-z0-9-]+)\.([^/]+)/(.+)$'
if [[ ! "${TARGET_ECR}" =~ ${ECR_URI_REGEX} ]]; then
  echo "Error: could not parse target-ecr-uri '${TARGET_ECR}'."
  echo "Expected form: <account>.dkr.ecr.<region>.<dns-suffix>/<repository-prefix>"
  exit 1
fi
ECR_ACCOUNT="${BASH_REMATCH[1]}"
ECR_REGION="${BASH_REMATCH[2]}"
ECR_DNS_SUFFIX="${BASH_REMATCH[3]}"
ECR_REPOSITORY_PREFIX="${BASH_REMATCH[4]}"
ECR_REGISTRY="${ECR_ACCOUNT}.dkr.ecr.${ECR_REGION}.${ECR_DNS_SUFFIX}"

echo "== Publishing jars to s3://${TARGET_BUCKET}/${ARTEFACTS_PREFIX}/ =="
"${THIS_DIR}/publishJarsToS3.sh" "${TARGET_BUCKET}" "${ARTEFACTS_PREFIX}"

echo "== Authenticating Docker to ${ECR_REGISTRY} =="
aws ecr get-login-password --region "${ECR_REGION}" \
    | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "== Ensuring ECR repositories exist under ${ECR_REPOSITORY_PREFIX}/${ARTEFACTS_PREFIX}/ =="
java -cp "${SCRIPTS_DIR}/jars/clients-${VERSION}-utility.jar" \
    sleeper.clients.deploy.container.EcrRepositoriesCreator \
    "${ECR_REPOSITORY_PREFIX}" "${ARTEFACTS_PREFIX}"

echo "== Publishing Docker images to ${TARGET_ECR}/${ARTEFACTS_PREFIX}/ =="
"${THIS_DIR}/publishDocker.sh" "${TARGET_ECR}/${ARTEFACTS_PREFIX}" "${CREATE_BUILDX}"

echo "== Publish complete =="
echo "Point instances at this release by setting the following properties:"
echo "  sleeper.artefacts.mode=published"
echo "  sleeper.jars.bucket=${TARGET_BUCKET}"
echo "  sleeper.ecr.repository.prefix=${ECR_REPOSITORY_PREFIX}"
echo "  sleeper.artefacts.prefix=${ARTEFACTS_PREFIX}"
echo "  # If the ECR is in a different account/region than the deploying instance, also set:"
echo "  sleeper.ecr.repository.account=${ECR_ACCOUNT}"
echo "  sleeper.ecr.repository.region=${ECR_REGION}"
