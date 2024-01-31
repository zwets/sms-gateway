#!/bin/bash

export LC_ALL="C"
set -euo pipefail

cd "$(dirname "$(realpath "$0")")/../.."

export SPRING_PROFILES_ACTIVE=test
exec mvn spring-boot:run
