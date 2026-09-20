#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_VERSION=8.10.2
GRADLE_HOME="$APP_HOME/.gradle-dist/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$APP_HOME/.gradle-dist"
  TMP="$APP_HOME/.gradle-dist/gradle.zip"
  curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$TMP"
  rm -rf "$GRADLE_HOME"
  unzip -q "$TMP" -d "$APP_HOME/.gradle-dist"
  rm -f "$TMP"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
