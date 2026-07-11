#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Detect path separator for the current platform.
PATH_SEP=":"
_SYS="$(uname -s 2>/dev/null)"
if [[ "$_SYS" == MINGW* ]] || [[ "$_SYS" == MSYS* ]] || [[ "$_SYS" == CYGWIN* ]]; then
  PATH_SEP=";"
fi

# Maven is only needed for the initial build. After that, lib/ and
# thirdparty/ant/lib/ contain everything required and the JVM is launched
# directly — no Maven at runtime.
NEEDS_BUILD=false
if [[ ! -f target/classes/${packageInPathFormat}/Start.class ]]; then
  NEEDS_BUILD=true
fi
if [[ ! -d lib ]] || [[ -z "$(find lib -maxdepth 1 -name '*.jar' -print -quit 2>/dev/null)" ]]; then
  NEEDS_BUILD=true
fi
if [[ ! -f thirdparty/ant/lib/ant-launcher.jar ]]; then
  NEEDS_BUILD=true
fi

if [[ "$NEEDS_BUILD" == true ]]; then
  echo "Building (compile + package)..."
  mvn -q package -DskipTests
fi

if [[ ! -d log ]]; then
  echo "Creating log directory"
  mkdir -p log
fi

if [[ ! -f jsystem.properties ]]; then
  echo "Creating jsystem.properties"
  SCRIPT_DIR="$(pwd)"
  if [[ "$_SYS" == MINGW* ]] || [[ "$_SYS" == MSYS* ]] || [[ "$_SYS" == CYGWIN* ]]; then
    WIN_DIR="$(cygpath -w "$SCRIPT_DIR" 2>/dev/null || echo "$SCRIPT_DIR" | sed 's|/|\\|g')"
    # Escape for Java properties: backslash -> \\, colon -> \:
    PROPS_DIR="$(printf '%s' "$WIN_DIR" | sed 's/\\/\\\\/g; s/:/\\:/g')"
    TESTS_SRC="${PROPS_DIR}\\\\src\\\\main\\\\java"
    TESTS_DIR="${PROPS_DIR}\\\\target\\\\classes"
    RESOURCES_SRC="${PROPS_DIR}\\\\src\\\\main\\\\resources"
  else
    PROPS_DIR="$SCRIPT_DIR"
    TESTS_SRC="${PROPS_DIR}/src/main/java"
    TESTS_DIR="${PROPS_DIR}/target/classes"
    RESOURCES_SRC="${PROPS_DIR}/src/main/resources"
  fi
  cat > jsystem.properties <<EOF
sutClassName=jsystem.framework.sut.SutImpl
logger=true
max.building.blocks.number=500
.level=INFO
jsystem.level=INFO
org.apache.http.client.level=INFO
org.apache.http.impl.level=INFO
org.apache.http.wire.level=INFO
org.apache.http.level=INFO
tests.src=${TESTS_SRC}
tests.dir=${TESTS_DIR}
resources.src=${RESOURCES_SRC}
htmlReportDir=log
sutFile=default.xml
currentScenario=scenarios/default
convert.old.scenarios=true
agent.client.list=local
reporter.classes=jsystem.extensions.report.simpleHtmlReporter.SimpleHtmlReporter
EOF
fi

CLASSPATH="target/classes${PATH_SEP}lib/*${PATH_SEP}thirdparty/ant/lib/*"
JAVA_CMD="java"
if [ -n "$JAVA_HOME" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi
echo "Launching JSystem Test Runner..."
exec "$JAVA_CMD" -Djsystem.main=jsystem.treeui.TestRunner -classpath "$CLASSPATH" ${package}.Start "$@"
