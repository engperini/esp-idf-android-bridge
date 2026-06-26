#!/bin/sh

# Lightweight Gradle launcher for environments where the standard Gradle
# wrapper JAR is not available. It downloads Gradle 8.7 on demand and runs it.

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
GRADLE_VERSION="8.7"
GRADLE_DIST_URL="${GRADLE_DIST_URL:-https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip}"
GRADLE_DIST_DIR="${GRADLE_DIST_DIR:-$HOME/.gradle-dist/gradle-${GRADLE_VERSION}}"

if [ ! -x "$GRADLE_DIST_DIR/bin/gradle" ]; then
  TMP_DIR=$(mktemp -d)
  trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
  mkdir -p "$GRADLE_DIST_DIR"
  curl -fsSL "$GRADLE_DIST_URL" -o "$TMP_DIR/gradle.zip"
  unzip -q "$TMP_DIR/gradle.zip" -d "$TMP_DIR"
  rm -rf "$GRADLE_DIST_DIR"
  mv "$TMP_DIR/gradle-${GRADLE_VERSION}" "$GRADLE_DIST_DIR"
fi

exec "$GRADLE_DIST_DIR/bin/gradle" -p "$APP_HOME" "$@"
