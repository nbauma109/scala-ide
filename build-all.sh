#!/bin/bash -e

BUILD_NUMBER=$(git rev-parse --short HEAD)

mvn wrapper:wrapper -Dmaven=3.9.6

# run in sequences the different maven calls needed to fully build Scala IDE from scratch

# not as good as the readlink version, but it is not working on os X
ROOT_DIR=$(dirname $0)
MVN_DIR=`pwd`
$MVN_DIR/mvnw --version
cd ${ROOT_DIR}
ROOT_DIR=${PWD}

if [ -z "$*" ]
then
  MVN_ARGS="-DbuildNumber=${BUILD_NUMBER} -Pscala-2.12.x -Peclipse-2024-03 $ADDITIONAL_MVN_OPTS clean install"
  MVN_P2_ARGS="-DbuildNumber=${BUILD_NUMBER} -Dtycho.localArtifacts=ignore -Pscala-2.12.x -Peclipse-2024-03 $ADDITIONAL_MVN_OPTS clean install"
else
  MVN_ARGS="-DbuildNumber=${BUILD_NUMBER} $*"
  MVN_P2_ARGS="-DbuildNumber=${BUILD_NUMBER} -Dtycho.localArtifacts=ignore $*"
fi

echo "Running with: mvnw ${MVN_P2_ARGS}"

# the parent project
echo "Building parent project in $ROOT_DIR"
cd ${ROOT_DIR}
$MVN_DIR/mvnw ${MVN_ARGS}

# set custom configuration files
echo "Setting custom configuration files"
$MVN_DIR/mvnw ${MVN_ARGS} -Pset-version-specific-files antrun:run


echo "Building the toolchain"
# the toolchain
cd ${ROOT_DIR}/org.scala-ide.build-toolchain
$MVN_DIR/mvnw ${MVN_ARGS}

echo "Generating the local p2 repositories"
# the local p2 repos
cd ${ROOT_DIR}/org.scala-ide.p2-toolchain
$MVN_DIR/mvnw -X ${MVN_P2_ARGS}

# set the versions if required
cd ${ROOT_DIR}
if [ -n "${SET_VERSIONS}" ]
then
  echo "setting versions"
  $MVN_DIR/mvnw ${MVN_P2_ARGS} -Pset-versions exec:java
else
  echo "Not running UpdateScalaIDEManifests."
fi

# the plugins
echo "Building plugins"
cd ${ROOT_DIR}/org.scala-ide.sdt.build
$MVN_DIR/mvnw ${MVN_P2_ARGS}

