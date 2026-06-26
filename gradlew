#!/bin/sh

# Minimal Gradle wrapper launcher for Unix-like systems.
# Uses gradle/wrapper/gradle-wrapper.jar and gradle/wrapper/gradle-wrapper.properties
# to download the requested Gradle distribution on demand.

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Gradle wrapper JAR not found: $WRAPPER_JAR" >&2
  exit 1
fi

JAVA_CMD="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" \
  ${DEFAULT_JVM_OPTS:-} \
  ${JAVA_OPTS:-} \
  ${GRADLE_OPTS:-} \
  -Dorg.gradle.appname=gradlew \
  -classpath "$WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain "$@"
