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

set -euo pipefail

IS_KOKORO="false"
if [[ -n "${KOKORO_ARTIFACTS_DIR:-}" ]] ; then
  IS_KOKORO="true"
fi
readonly IS_KOKORO

if [[ "${IS_KOKORO}" == "true" ]] ; then
  TINK_BASE_DIR="$(echo "${KOKORO_ARTIFACTS_DIR}"/git*)"
  cd "${TINK_BASE_DIR}/tink_java_awskms"
fi

: "${TINK_BASE_DIR:=$(cd .. && pwd)}"

# Check for dependencies in TINK_BASE_DIR. Any that aren't present will be
# downloaded.
readonly GITHUB_ORG="https://github.com/tink-crypto"
./kokoro/testutils/fetch_git_repo_if_not_present.sh "${TINK_BASE_DIR}" \
  "${GITHUB_ORG}/tink-java"

./kokoro/testutils/replace_http_archive_with_local_repository.py \
  -f "WORKSPACE" -t "${TINK_BASE_DIR}"
./kokoro/testutils/copy_credentials.sh "testdata" "aws"

# Install the latest snapshots locally.
(
  cd "${TINK_BASE_DIR}/tink_java"
  ./maven/maven_deploy_library.sh install tink \
    maven/tink-java.pom.xml HEAD
)
./maven/maven_deploy_library.sh install tink-awskms \
  maven/tink-java-awskms.pom.xml HEAD

readonly AWS_CREDENTIALS="testdata/aws/credentials.cred"
readonly AWS_TEST_KEY_URI="aws-kms://arn:aws:kms:us-east-2:235739564943:key/3ee50705-5a82-4f5b-9753-05c4f473922f"

# Run the local test Maven example.
mvn package --no-snapshot-updates -f examples/maven/pom.xml
mvn exec:java --no-snapshot-updates -f examples/maven/pom.xml \
  -Dexec.args="keyset.json ${AWS_CREDENTIALS} ${AWS_TEST_KEY_URI}"

readonly GITHUB_JOB_NAME="tink/github/java_awskms/gcp_ubuntu/maven/continuous"

if [[ "${IS_KOKORO}" == "true" \
      && "${KOKORO_JOB_NAME}" == "${GITHUB_JOB_NAME}" ]]; then
  # GITHUB_ACCESS_TOKEN is populated by Kokoro.
  readonly GIT_CREDENTIALS="ise-crypto:${GITHUB_ACCESS_TOKEN}"
  readonly GITHUB_URL="https://${GIT_CREDENTIALS}@github.com/tink-crypto/tink-java-awskms.git"
  ./maven/maven_deploy_library.sh -u "${GITHUB_URL}" snapshot tink-awskms \
    maven/tink-java-awskms.pom.xml HEAD
fi
