#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $(basename "$0") <patch|minor|major> [--no-push]" >&2
}

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

BUMP="$1"
shift
PUSH=1
while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-push) PUSH=0 ;;
        *) echo "Unknown argument: $1" >&2; usage; exit 1 ;;
    esac
    shift
done

case "$BUMP" in
    patch|minor|major) ;;
    *) usage; exit 1 ;;
esac

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean. Commit or stash changes first." >&2
    exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
    echo "Refusing to release from branch '$CURRENT_BRANCH'. Switch to main first." >&2
    exit 1
fi

CURRENT="$(awk -F= '/^mod_version/ {gsub(/ /,"",$2); print $2}' gradle.properties)"
if [[ -z "$CURRENT" ]]; then
    echo "Could not read mod_version from gradle.properties." >&2
    exit 1
fi

if [[ ! "$CURRENT" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "mod_version '$CURRENT' is not a clean MAJOR.MINOR.PATCH (pre-release suffixes not supported)." >&2
    exit 1
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

case "$BUMP" in
    patch) PATCH=$((PATCH + 1)) ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
esac

NEW="${MAJOR}.${MINOR}.${PATCH}"
TAG="v${NEW}"

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
    echo "Tag ${TAG} already exists." >&2
    exit 1
fi

echo "Bumping mod_version: ${CURRENT} -> ${NEW}"
echo "Verifying build before tagging..."
./gradlew build

TMP="$(mktemp)"
awk -v new="$NEW" '
    /^mod_version[[:space:]]*=/ { print "mod_version=" new; next }
    { print }
' gradle.properties > "$TMP"
mv "$TMP" gradle.properties

git add gradle.properties
git commit -m "release: ${TAG}"
git tag -a "${TAG}" -m "${TAG}"

PUSH_STATUS="skipped"
if [[ "$PUSH" -eq 1 ]]; then
    git push origin HEAD
    git push origin "${TAG}"
    PUSH_STATUS="pushed"
fi

echo
echo "Release summary:"
echo "  version: ${CURRENT} -> ${NEW}"
echo "  tag:     ${TAG}"
echo "  push:    ${PUSH_STATUS}"
