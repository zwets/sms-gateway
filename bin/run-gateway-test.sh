#!/bin/bash

export LC_ALL="C"
set -euo pipefail

cd "$(dirname "$(realpath "$0")")/.."

VERSION=1.0.4-SNAPSHOT
JAR="target/sms-gateway-${VERSION}.jar"

exec java -jar "$JAR" --spring.profiles.active='test' "$@"
