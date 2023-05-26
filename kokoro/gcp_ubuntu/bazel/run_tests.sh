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

# By default when run locally this script runs the command below directly on the
# host. The CONTAINER_IMAGE variable can be set to run on a custom container
# image for local testing. E.g.:
#
# CONTAINER_IMAGE="us-docker.pkg.dev/tink-test-infrastructure/tink-ci-images/linux-tink-cc-cmake:latest" \
#  sh ./kokoro/gcp_ubuntu/bazel_fips/run_tests.sh
#
# The user may specify TINK_BASE_DIR as the folder where to look for
# tink-java-awskms and its depndencies. That is:
#   ${TINK_BASE_DIR}/tink_java
#   ${TINK_BASE_DIR}/tink_java_awskms
set -eEuo pipefail

readonly C_PREFIX="us-docker.pkg.dev/tink-test-infrastructure/tink-ci-images"
readonly GITHUB_ORG="https://github.com/tink-crypto"

_create_test_command() {
  cat <<'EOF' > _do_run_test.sh
set -euo pipefail

BAZEL_CMD="bazel"
# Prefer using Bazelisk if available.
if command -v "bazelisk" &> /dev/null; then
  BAZEL_CMD="bazelisk"
fi
readonly BAZEL_CMD

#######################################
# Prints and error message with the missing deps for the given target diff-ing
# the expected and actual list of targets.
#
# Globals:
#   None
# Arguments:
#   target: Bazel target.
#   expected_deps: Expected list of dependencies.
#   actual_deps: Actual list of dependencies.
# Outputs:
#   Writes to stdout
#######################################
print_missing_deps() {
  local -r target="$1"
  local -r expected_deps="$2"
  local -r actual_deps="$3"

  echo "#========= ERROR ${target} target:"
  echo "The following dependencies are missing from the ${target} target:"
  diff --changed-group-format='%>' --unchanged-group-format='' \
    "${actual_deps}" "${expected_deps}"
  echo "#==============================="
}

#######################################
# Checks if the //:tink-awskms has all the required dependencies.
#
# Globals:
#   None
# Arguments:
#   None
# Outputs:
#   Writes to stdout
#######################################
test_build_bazel_file() {
  local -r tink_java_prefix="//src/main/java/com/google/crypto/tink"
  local -r tink_java_integration_awskms_prefix="${tink_java_prefix}/integration/awskms"

  # Targets in tink_java_integration_awskms_prefix of type java_library,
  # excluding testonly targets.
  local -r expected_awskms_deps="$(mktemp)"
  "${BAZEL_CMD}" query "\
kind(java_library,${tink_java_integration_awskms_prefix}/...) \
except attr(testonly,1,${tink_java_integration_awskms_prefix}/...)" \
    > "${expected_awskms_deps}"

  # Dependencies of //:tink-awskms of type java_library that are in
  # tink_java_integration_awskms_prefix.
  # Note: Considering only direct dependencies of the target.
  local -r actual_awskms_targets="$(mktemp)"
  "${BAZEL_CMD}" query "filter(\
${tink_java_integration_awskms_prefix},\
kind(java_library,deps(//:tink-awskms,1)))" \
    > "${actual_awskms_targets}"

  if ! cmp -s "${actual_awskms_targets}" "${expected_awskms_deps}"; then
    print_missing_deps "//:tink-awskms" "${expected_awskms_deps}" \
      "${actual_awskms_targets}"
    exit 1
  fi
}

main() {
  test_build_bazel_file
  ./kokoro/testutils/run_bazel_tests.sh .
}

main "$@"
EOF

  chmod +x _do_run_test.sh
}

cleanup() {
  rm -rf _do_run_test.sh
  mv WORKSPACE.bak WORKSPACE
}

main() {
  local run_command_args=()
  if [[ -n "${KOKORO_ARTIFACTS_DIR:-}" ]] ; then
    TINK_BASE_DIR="$(echo "${KOKORO_ARTIFACTS_DIR}"/git*)"
    local -r c_name="linux-tink-java-base"
    local -r c_hash="f5b13615141aed34da573698c56a803b5dd7a7f740f4ed9d9dac31e20f860a1a"
    CONTAINER_IMAGE="${C_PREFIX}/${c_name}@sha256:${c_hash}"
    run_command_args+=( -k "${TINK_GCR_SERVICE_KEY}" )
  fi
  : "${TINK_BASE_DIR:=$(cd .. && pwd)}"
  readonly TINK_BASE_DIR
  readonly CONTAINER_IMAGE

  if [[ -n "${CONTAINER_IMAGE}" ]]; then
    run_command_args+=( -c "${CONTAINER_IMAGE}" )
  fi
  readonly run_command_args

  cd "${TINK_BASE_DIR}/tink_java_awskms"

  # Check for dependencies in TINK_BASE_DIR. Any that aren't present will be
  # downloaded.
  ./kokoro/testutils/fetch_git_repo_if_not_present.sh "${TINK_BASE_DIR}" \
    "${GITHUB_ORG}/tink-java"

  cp WORKSPACE WORKSPACE.bak

  ./kokoro/testutils/replace_http_archive_with_local_repository.py \
    -f WORKSPACE -t ..

  _create_test_command

  # Run cleanup on EXIT.
  trap cleanup EXIT

  ./kokoro/testutils/run_command.sh "${run_command_args[@]}" ./_do_run_test.sh
}

main "$@"
