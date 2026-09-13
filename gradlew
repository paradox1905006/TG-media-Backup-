#!/bin/sh
#
# Gradle wrapper — run this instead of "gradle" directly.
# It auto-downloads the correct Gradle version on first run.
# Usage:
#   ./gradlew assembleDebug    → builds debug APK
#   ./gradlew assembleRelease  → builds release APK
#   ./gradlew tasks            → list all available tasks

# Resolve where the script lives
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$SCRIPT_DIR"

# Java is required — make sure JAVA_HOME is set or java is on PATH
if [ -z "$JAVA_HOME" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
  else
    echo "ERROR: Java not found. Install JDK 17+ and set JAVA_HOME." >&2
    exit 1
  fi
else
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Launch the wrapper JAR
exec "$JAVA_CMD" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
