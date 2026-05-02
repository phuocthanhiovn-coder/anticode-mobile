#!/bin/sh
# Gradle wrapper script for Linux/Mac (GitHub Actions)
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Resolve JAVA_HOME
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Determine the project directory
APP_HOME=$( cd "${DIRNAME}" > /dev/null && pwd -P )

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Download wrapper jar if missing
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle Wrapper..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
