#!/bin/bash

export LC_ALL="C"
set -euo pipefail

cd "$(dirname "$(realpath "$0")")/.."

# This asseumes a symlink in our base directory pointing at the versioned jar
JAR="sms-gateway.jar"
exec java -jar "$JAR" --spring.profiles.active='test' "$@"
