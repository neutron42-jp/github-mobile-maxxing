#!/usr/bin/env bash
# Local regression checks for the release-tag validation introduced for S01.
#
# These checks run the validator directly with ordinary fixtures. They never
# invoke a workflow, contact GitHub, or use signing material.

set -uo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/../.." && pwd)"
VALIDATOR="${REPO_ROOT}/scripts/validate-release-tag.sh"
RELEASE_YML="${REPO_ROOT}/.github/workflows/release.yml"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

PASS=0
FAIL=0

pass() {
  PASS=$((PASS + 1))
}

fail() {
  FAIL=$((FAIL + 1))
  printf 'FAIL: %s\n' "$1" >&2
}

check_valid() {
  local tag="$1" expected="$2" status
  bash "$VALIDATOR" "$tag" >"${TMP_DIR}/out" 2>"${TMP_DIR}/err"
  status=$?
  if [ "$status" -eq 0 ] &&
    cmp -s "${TMP_DIR}/out" <(printf '%s\n' "$expected") &&
    [ ! -s "${TMP_DIR}/err" ]; then
    pass
  else
    fail "expected valid tag '${tag}' to print '${expected}' (status ${status})"
  fi
}

check_invalid() {
  local tag="$1" status
  bash "$VALIDATOR" "$tag" >"${TMP_DIR}/out" 2>"${TMP_DIR}/err"
  status=$?
  if [ "$status" -ne 0 ] && [ ! -s "${TMP_DIR}/out" ] && [ -s "${TMP_DIR}/err" ]; then
    pass
  else
    fail "expected invalid tag '${tag}' to be rejected (status ${status})"
  fi
}

check_arity() {
  local label="$1" status
  shift
  bash "$VALIDATOR" "$@" >"${TMP_DIR}/out" 2>"${TMP_DIR}/err"
  status=$?
  if [ "$status" -ne 0 ] && [ ! -s "${TMP_DIR}/out" ]; then
    pass
  else
    fail "expected ${label} to be rejected"
  fi
}

check_contains() {
  local label="$1" pattern="$2" file="$3"
  if grep -Fq -- "$pattern" "$file"; then
    pass
  else
    fail "${label}: expected to find '${pattern}'"
  fi
}

check_line_count() {
  local label="$1" pattern="$2" file="$3" expected="$4" actual
  actual="$(grep -Fc -- "$pattern" "$file" 2>/dev/null || true)"
  if [ "$actual" -eq "$expected" ]; then
    pass
  else
    fail "${label}: expected ${expected} occurrence(s) of '${pattern}', found ${actual}"
  fi
}

# --- Deployed release-tag format stays accepted -----------------------------
check_valid 'v1.0.0' 'v1.0.0'
check_valid 'v1.0.9' 'v1.0.9'
check_valid 'v1.0.10' 'v1.0.10'
check_valid 'v12.34.567' 'v12.34.567'

# --- Ordinary malformed tags are rejected -----------------------------------
check_invalid ''
check_invalid '1.0.0'
check_invalid 'V1.0.0'
check_invalid 'v1'
check_invalid 'v1.0'
check_invalid 'v1.0.0.0'
check_invalid 'v1..0'
check_invalid 'v1.0.'
check_invalid 'vv1.0.0'
check_invalid 'v1.0.0-rc1'
check_invalid 'v1.0.0+build'
check_invalid 'release-v1.0.0'

# --- Whitespace and line breaks are rejected --------------------------------
check_invalid ' v1.0.0'
check_invalid 'v1.0.0 '
check_invalid $'v1.0.0\t'
check_invalid $'v1\t.0.0'
check_invalid $'v1.0.0\nv1.0.1'
check_invalid $'v1.0.0\n'
check_invalid $'v1.0.0\r\n'
check_invalid $'\nv1.0.0'
check_invalid $'v1.0.0\n\n'

# --- Oversized input is rejected --------------------------------------------
check_invalid "v$(printf '1%.0s' {1..260}).0.0"

# --- Invocation contract ----------------------------------------------------
check_arity 'no arguments'
check_arity 'two arguments' 'v1.0.0' 'v1.0.1'

# --- Workflow wiring --------------------------------------------------------
check_line_count 'tag input is read once' 'inputs.tag' "$RELEASE_YML" 1
check_contains 'tag is passed through env' 'RELEASE_TAG: ${{ github.event_name == '"'"'workflow_dispatch'"'"' && inputs.tag || github.ref_name }}' "$RELEASE_YML"
check_contains 'tag is validated through the script' 'TAG_NAME="$(bash scripts/validate-release-tag.sh "$RELEASE_TAG")"' "$RELEASE_YML"
check_contains 'validated tag is written to the environment file' $'printf \'TAG_NAME=%s\\n\' "$TAG_NAME" >> "$GITHUB_ENV"' "$RELEASE_YML"
check_contains 'validated tag feeds the Gradle version name' 'ANDROID_VERSION_NAME=%s' "$RELEASE_YML"
check_contains 'version name is written to the environment file' $'printf \'ANDROID_VERSION_NAME=%s\\n\' "${TAG_NAME#v}" >> "$GITHUB_ENV"' "$RELEASE_YML"
check_contains 'version name strips only the leading v' '"${TAG_NAME#v}"' "$RELEASE_YML"
check_contains 'publish step consumes the validated tag' 'gh release view "$TAG_NAME"' "$RELEASE_YML"

resolve_line="$(grep -nF 'name: Resolve and validate release tag' "$RELEASE_YML" | cut -d: -f1)"
keystore_line="$(grep -nF 'name: Decode Android release keystore' "$RELEASE_YML" | cut -d: -f1)"
if [ -n "$resolve_line" ] && [ -n "$keystore_line" ] && [ "$resolve_line" -lt "$keystore_line" ]; then
  pass
else
  fail 'release tag must be validated before the signing keystore is prepared'
fi

printf 'validate-release-tag tests: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
