#!/usr/bin/env bash
# Validate a GitHub release tag before it is used for signing or publishing.
#
# The accepted format is the one already deployed in this repository:
# vMAJOR.MINOR.PATCH (for example v1.0.0 or v1.2.10). Inputs that are empty,
# contain whitespace, span multiple lines, or carry pre-release/build suffixes
# are rejected. On success the validated tag is printed as a single line so the
# caller can pass it to the workflow environment file without further parsing.

set -euo pipefail

readonly RELEASE_TAG_PATTERN='^v[0-9]+\.[0-9]+\.[0-9]+$'
readonly RELEASE_TAG_MAX_LENGTH=255

die() {
  printf 'validate-release-tag: %s\n' "$1" >&2
  exit 2
}

main() {
  if [ "$#" -ne 1 ]; then
    die "expected exactly one argument"
  fi

  local tag="$1"

  if [ -z "$tag" ]; then
    die "release tag is empty"
  fi

  # The environment file protocol is line based, so reject line breaks
  # explicitly instead of relying on the anchored pattern alone.
  case "$tag" in
    *$'\n'* | *$'\r'*)
      die "release tag must be a single line"
      ;;
  esac

  if [ "${#tag}" -gt "$RELEASE_TAG_MAX_LENGTH" ]; then
    die "release tag is longer than ${RELEASE_TAG_MAX_LENGTH} characters"
  fi

  if [[ ! "$tag" =~ $RELEASE_TAG_PATTERN ]]; then
    die "release tag must match vMAJOR.MINOR.PATCH (for example v1.0.1)"
  fi

  printf '%s\n' "$tag"
}

main "$@"
