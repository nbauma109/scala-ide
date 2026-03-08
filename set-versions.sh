#!/bin/bash

set -euo pipefail

MVN_CMD="${MVN_CMD:-mvn}"

function usage() {
    cat <<EOF

usage:
    set-versions.sh <version>

Delegate to the maven-tycho-plugin to set version of all subprojects. It maintains the POM and
MANIFEST version numbers in sync. This script runs maven on each parent project.

Note:
    Use only numeric versions (do not specify -SNAPSHOT at the end, it is added by this script).

Example:
    set-versions.sh 3.0.3

    This sets the pom version to 3.0.3-SNAPSHOT and the Bundle-Version to 3.0.3.qualifier

EOF
    exit 1
}

# $1 - version number to validate
function validate() {

    if [[ ! $1 =~ [0-9]+\.[0-9]+\.[0-9]+$ ]];
    then
        echo "Invalid version: $1. Versions should have 3 numeric components (i.e. 1.0.1) with no qualifier or suffix."
        exit 1
    fi
}

if [ $# -ne 1 ]; then
    usage
fi

function setVersion() {
    "${MVN_CMD}" -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$1-SNAPSHOT
}

function rootPomVersion() {
    sed -n 's|^[[:space:]]*<version>\(.*\)</version>[[:space:]]*$|\1|p' pom.xml | head -n 1
}

function syncNonPomVersions() {
    local old_snapshot="$1"
    local new_base="$2"
    local old_base="${old_snapshot%-SNAPSHOT}"
    local old_feature="${old_base}.qualifier"
    local new_snapshot="${new_base}-SNAPSHOT"
    local new_feature="${new_base}.qualifier"

    while IFS= read -r target_file; do
        perl -0pi -e "s#(<artifactId>org\\.scala-ide\\.zinc\\.(?:compiler\\.bridge|library)</artifactId>\\s*<version>)\Q${old_snapshot}\E(</version>)#\\1${new_snapshot}\\2#gms" "${target_file}"
    done < <(git ls-files 'target-platform/*.target')

    while IFS= read -r feature_file; do
        perl -0pi -e "s#version=\"\Q${old_feature}\E\"#version=\"${new_feature}\"#g" "${feature_file}"
    done < <(git ls-files '*feature.xml')

    while IFS= read -r manifest_file; do
        perl -0pi -e "s#(Bundle-Version:\\s*)\Q${old_feature}\E#\\1${new_feature}#g" "${manifest_file}"
    done < <(git ls-files '*MANIFEST.MF' '*MANIFEST-*.MF')
}

validate "$1"

echo "Setting version to $1"

old_version="$(rootPomVersion)"
if [[ -z "${old_version}" ]]; then
    echo "Unable to determine current version from pom.xml"
    exit 1
fi

setVersion "$1"

(cd org.scala-ide.build-toolchain && setVersion "$1")
(cd org.scala-ide.sdt.build && setVersion "$1")
(cd org.scala-ide.p2-toolchain && setVersion "$1")

syncNonPomVersions "${old_version}" "$1"
