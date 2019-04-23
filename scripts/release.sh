#!/bin/bash
# Release script for kti
USAGE="
./scripts/release.sh <tag> [--no-compile]
Release script for kti.
Must be called with a tag as first argument. Creates the tag if needed.
If the --no-compile flag is parsed, `lein clean` and `lein uberjar`
are not called, and an appropriate `./target/uberjar/kti.jar` file is
assumed to exist.

The file `./scripts/.release_env` must exist and export the env var
GITHUB_TOKEN, with the github token that can access github api for the
kti repo.
"

[ -z "$1" ]\
    && echo "ERROR: No tag specified" >/dev/stderr\
    && echo "$USAGE" >/dev/stderr\
    && exit 1
TAG="$1"

NOCOMPILE=0
echo "$@" | grep -- '--no-compile' && NOCOMPILE=1

# Expect GITHUB_TOKEN to be exported from .release_env
source ./scripts/.release_env
[ -z "$GITHUB_TOKEN" ]\
    && echo "No github token specified." >/dev/stderr\
    && echo "$USAGE" >/dev/stderr\
    && exit 1

REPO_URL='https://api.github.com/repos/vitorqb/kti/'
JARFILE=./target/uberjar/kti.jar

function compile() {
    echo "-> Compiling..."
    lein clean && lein uberjar
}

function maybe_create_tag() {
    if ! git tag | grep "$TAG" 
    then
        echo "-> Creating tag $TAG"
        git tag -a "$TAG" -m "$TAG"
    else
        echo "-> Tag already exists"
    fi
}

function push_tag() {
    echo "Pushing tag $TAG"
    git push origin "$TAG"
}

function create_release() {
    echo "-> Creating release with github rest api"
    curl -H "Authorization: token ${GITHUB_TOKEN}" --data @- "${REPO_URL}releases" <<EOF
{
  "tag_name": "$TAG",
  "name": "$TAG",
  "draft": false,
  "prerelease": false
}
EOF
}

function get_upload_url() {
    curl -s -H "Authorization: token $GITHUB_TOKEN" "${REPO_URL}releases/tags/$TAG" | jq '.upload_url' | sed -r -e 's/"//g' -e 's/\{.+\}//'
}

function upload_jar() {
    echo "-> Uploading jar..."
    URL="$(get_upload_url)?name=kti.jar"
    curl -H "Authorization: token $GITHUB_TOKEN" -H 'Content-Type: application/java-archive' --data @$JARFILE $URL
}

# For debugging and development
[ "$DRYRUN" = "1" ] && return 0

[ "$NOCOMPILE" = 1 ]\
    || compile\
    && maybe_create_tag\
    && push_tag\
    && create_release\
    && upload_jar
