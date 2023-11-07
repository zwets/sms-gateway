#!/bin/bash

export LC_ALL="C"
set -euo pipefail
cd "$(dirname "$0")/../target"

JAR=sms-gateway-*-SNAPSHOT.jar
exec java -jar $JAR --spring.profiles.active=test "$@"
