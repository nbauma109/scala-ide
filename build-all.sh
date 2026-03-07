#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_NUMBER="${BUILD_NUMBER:-$(git -C "${ROOT_DIR}" rev-parse --short HEAD)}"
SCALA_STREAMS="${SCALA_STREAMS:-scala-2.12 scala-2.13}"

if [[ $# -eq 0 ]]; then
  GOALS=(clean install)
else
  GOALS=("$@")
fi

run_mvn() {
  local dir="$1"
  shift
  echo "==> (${dir}) mvn $*"
  (cd "${dir}" && mvn "$@")
}

run_stream() {
  local stream_profile="$1"
  local stream_label="$2"
  local common_opts=(-DbuildNumber="${BUILD_NUMBER}" -Peclipse-2024-03 "-P${stream_profile}")
  if [[ -n "${ADDITIONAL_MVN_OPTS:-}" ]]; then
    local -a extra_opts
    read -r -a extra_opts <<< "${ADDITIONAL_MVN_OPTS}"
    common_opts+=("${extra_opts[@]}")
  fi
  local p2_opts=("${common_opts[@]}")
  local sdt_opts=("${common_opts[@]}")

  if [[ -n "${SCALA_IDE_COMPILE_CLASSPATH:-}" ]]; then
    sdt_opts+=("-Dscala.ide.compile.classpath=${SCALA_IDE_COMPILE_CLASSPATH}")
  fi

  echo
  echo "==================================================================="
  echo "Building Scala stream ${stream_label} (${stream_profile})"
  echo "==================================================================="

  run_ "${ROOT_DIR}" "${common_opts[@]}" "${GOALS[@]}"
  run_ "${ROOT_DIR}" "${common_opts[@]}" -Pset-version-specific-files antrun:run
  run_mvn "${ROOT_DIR}/org.scala-ide.build-toolchain" "${common_opts[@]}" "${GOALS[@]}"
  run_mvn "${ROOT_DIR}/org.scala-ide.p2-toolchain" "${p2_opts[@]}" "${GOALS[@]}"

  if [[ -n "${SET_VERSIONS:-}" ]]; then
    run_mvn "${ROOT_DIR}" "${p2_opts[@]}" -Pset-versions exec:java
  fi

  run_mvn "${ROOT_DIR}/org.scala-ide.sdt.build" "${sdt_opts[@]}" "${GOALS[@]}"
}

echo "Tycho builds require JDK 17 or newer."
echo "JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "BUILD_NUMBER=${BUILD_NUMBER}"
echo "STREAMS=${SCALA_STREAMS}"
echo "GOALS=${GOALS[*]}"
if [[ -n "${SCALA_IDE_COMPILE_CLASSPATH:-}" ]]; then
  echo "SCALA_IDE_COMPILE_CLASSPATH is set."
else
  echo "SCALA_IDE_COMPILE_CLASSPATH is not set."
fi
mvn --version

for stream in ${SCALA_STREAMS}; do
  case "${stream}" in
    scala-2.12) run_stream "scala-2.12" "2.12.21" ;;
    scala-2.13) run_stream "scala-2.13" "2.13.18" ;;
    *)
      echo "Unknown stream profile: ${stream}"
      exit 1
      ;;
  esac
done
