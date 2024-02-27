#!/bin/bash

export LC_ALL="C"
set -euo pipefail

cd "$(dirname "$(realpath "$0")")/.."

[ -f config/live.vault ] || {
    cp src/main/resources/builtin.vault config/live.vault
    keytool -genkeypair -keyalg RSA -keysize 2048 -validity 36500 -storepass 123456 -keystore config/live.vault -storetype PKCS12 -alias live -dname CN=live
}

export SPRING_PROFILES_ACTIVE=test
exec mvn spring-boot:run
