#!/bin/bash
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

# Builds and tests tink-java-awskms and its examples using Maven.
#
# The behavior of this script can be modified using the following optional env
# variables:
#
# - CONTAINER_IMAGE (unset by default): By default when run locally this script
#   executes tests directly on the host. The CONTAINER_IMAGE variable can be set
#   to execute tests in a custom container image for local testing. E.g.:
#
#   CONTAINER_IMAGE="us-docker.pkg.dev/tink-test-infrastructure/tink-ci-images/linux-tink-java-base:latest" \
#     sh ./kokoro/gcp_ubuntu/maven/run_tests.sh

# Generated with openssl rand -hex 10
echo "==========================================================================="
echo "Tink Script ID: 39a481eb3e1aff6478e3 (to quickly find the script from logs)"
echo "==========================================================================="

set -eEuo pipefail

IS_KOKORO="false"
if [[ -n "${KOKORO_ARTIFACTS_DIR:-}" ]] ; then
  IS_KOKORO="true"
fi
readonly IS_KOKORO

RUN_COMMAND_ARGS=()
if [[ "${IS_KOKORO}" == "true" ]] ; then
  readonly TINK_BASE_DIR="$(echo "${KOKORO_ARTIFACTS_DIR}"/git*)"
  cd "${TINK_BASE_DIR}/tink_java_awskms"
  source ./kokoro/testutils/java_test_container_images.sh
  CONTAINER_IMAGE="${TINK_JAVA_BASE_IMAGE}"
  RUN_COMMAND_ARGS+=( -k "${TINK_GCR_SERVICE_KEY}" )
fi
readonly CONTAINER_IMAGE

if [[ -n "${CONTAINER_IMAGE:-}" ]]; then
  RUN_COMMAND_ARGS+=( -c "${CONTAINER_IMAGE}" )
fi

# File that stores environment variables to pass to the container.
readonly ENV_VARIABLES_FILE="/tmp/env_variables.txt"

if [[ -n "${TINK_REMOTE_BAZEL_CACHE_GCS_BUCKET:-}" ]]; then
  cp "${TINK_REMOTE_BAZEL_CACHE_SERVICE_KEY}" ./cache_key
  cat <<EOF > "${ENV_VARIABLES_FILE}"
BAZEL_REMOTE_CACHE_NAME=${TINK_REMOTE_BAZEL_CACHE_GCS_BUCKET}/bazel/${TINK_JAVA_BASE_IMAGE_HASH}
EOF
  RUN_COMMAND_ARGS+=( -e "${ENV_VARIABLES_FILE}" )
fi

cat <<'EOF' > _do_run_test.sh
set -euo pipefail

# Ignore com.google.crypto.tink:tink; this is a Bazel dependency, not a Maven one.
# TODO: b/332650707 - Re-enable this check once the script is fixed s.t. it
# works with Bazel 8.
# ./kokoro/testutils/check_maven_bazel_deps_consistency.sh \
#   -e "com.google.crypto.tink:tink" "//:tink-awskms" \
#   "maven/tink-java-awskms.pom.xml"

MAVEN_DEPLOY_LIBRARY_OPTS=()
if [[ -n "${BAZEL_REMOTE_CACHE_NAME:-}" ]]; then
  MAVEN_DEPLOY_LIBRARY_OPTS+=( -c "${BAZEL_REMOTE_CACHE_NAME}" )
fi
readonly MAVEN_DEPLOY_LIBRARY_OPTS

./maven/maven_deploy_library.sh "${MAVEN_DEPLOY_LIBRARY_OPTS[@]}" install \
  tink-awskms maven/tink-java-awskms.pom.xml HEAD

readonly AWS_CREDENTIALS="testdata/aws/credentials.cred"
readonly AWS_TEST_KEY_URI="aws-kms://arn:aws:kms:us-east-2:235739564943:key/3ee50705-5a82-4f5b-9753-05c4f473922f"

# Run the local test Maven example.
mvn package --no-snapshot-updates -f examples/maven/pom.xml
mvn exec:java --no-snapshot-updates -f examples/maven/pom.xml \
  -Dexec.args="keyset.json ${AWS_CREDENTIALS} ${AWS_TEST_KEY_URI}"
EOF

chmod +x _do_run_test.sh

./kokoro/testutils/copy_credentials.sh "testdata" "aws"

# Run cleanup on EXIT.
trap cleanup EXIT

cleanup() {
  rm -rf _do_run_test.sh "${ENV_VARIABLES_FILE}"
}

./kokoro/testutils/docker_execute.sh "${RUN_COMMAND_ARGS[@]}" ./_do_run_test.sh

readonly GITHUB_JOB_NAME="tink/github/java_awskms/gcp_ubuntu/maven/continuous"

if [[ "${IS_KOKORO}" == "true" \
      && "${KOKORO_JOB_NAME}" == "${GITHUB_JOB_NAME}" ]]; then
  # GITHUB_ACCESS_TOKEN is populated by Kokoro.
  readonly GIT_CREDENTIALS="ise-crypto:${GITHUB_ACCESS_TOKEN}"
  readonly GITHUB_URL="https://${GIT_CREDENTIALS}@github.com/tink-crypto/tink-java-awskms.git"

  # Share the required env variables with the container to allow publishing the
  # snapshot on Sonatype.
  cat <<EOF >> "${ENV_VARIABLES_FILE}"
SONATYPE_USERNAME
SONATYPE_PASSWORD
EOF

  ./kokoro/testutils/docker_execute.sh "${RUN_COMMAND_ARGS[@]}" \
    ./maven/maven_deploy_library.sh -u "${GITHUB_URL}" snapshot tink-awskms \
      maven/tink-java-awskms.pom.xml HEAD
fi
