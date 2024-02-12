#!/bin/bash

export LC_ALL="C"
set -euo pipefail

# Resolve the sms-client tool in its repo or else deployed or else from SMS_CLIENT
sms_client() {
    declare -g SMS_CLIENT
    SMS_CLIENT="${SMS_CLIENT:-$(realpath -e "$(dirname "$(realpath "$0")")/../../../sms-client/bin/sms-client" 2>/dev/null)}"
    SMS_CLIENT="${SMS_CLIENT:-$(realpath -e "$(dirname "$(realpath "$0")")/../../sms-client/bin/sms-client" 2>/dev/null)}"
    SMS_CLIENT="${SMS_CLIENT:-$(realpath -e "/opt/sms-client/bin/sms-client" 2>/dev/null)}"
    [ -n "$SMS_CLIENT" ] && [ -x "$SMS_CLIENT" ] || err_exit "sms-client not found; set its path in lib/defaults"
    "$SMS_CLIENT" "$@"
}

# Encrypt stdin to stdout with pubkey $1
pubkey_encrypt() {
    sms_client encrypt "$1"
}

# Output encrypted SMS with pubkey $1 to recipient $2 from sender $3 from optional file $4
encrypted_sms() {
    { printf 'To: %s\nSender: %s\n\n' "$2" "$3"; cat "${4:--}"; } | sms_client encrypt "$1"
}

# vim: sts=4:sw=4:ai:si:et
