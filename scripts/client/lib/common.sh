#!/bin/bash

export LC_ALL="C"
set -euo pipefail

# Defaults

export BROKER="${BROKER:-localhost:9192}"
export TOPIC="${TOPIC:-send-sms}"
export PARTITION="${PARTITION:-}"
export EVENTKEY="${EVENTKEY:-}"
export GROUPID="${GROUPID:-}"
export OFFSET="${OFFSET:-}"
export DUMP=${DUMP:-}
export VERBOSE=${VERBOSE:-}

export USAGE="${USAGE:-}

  COMMON OPTIONS
   -b,--broker=BROKER  Set the broker host:port [$BROKER]
   -t,--topic=TOPIC    Set the topic [$TOPIC]
   -p,--partition=P    Set the partition [$PARTITION]
   -k,--key=KEY        Set the event key [$EVENTKEY]
   -g,--group=GID      Group ID of client [$GROUPID]
   -o,--offset=OFFSET  Start reading at offset [$OFFSET]
   -a,--all            Read the topic from the beginning
   -d,--dump           Write input to stderr / full output
   -v,--verbose        Display diagnostic output
   -h,--help           Display usage information
"
# General functions

emit() { [ ! $VERBOSE ] || echo "${0##*/}: $*" >&2; }
usage_exit() { echo "Usage: ${0##*/} [-dvha] [-b BROKER ] [-t TOPIC] [-p PARTITION] [-k KEY] [-g GROUP] [-o OFFSET] ${USAGE}" >&2; exit ${1:-1}; }
err_exit() { echo "${0##*/}: $*" >&2; exit 1; }

# Check common options

TEMP=$(getopt -n "${0##*/}" -o 'hvdab:t:p:k:g:o:' -l 'help,verbose,dump,all,broker,topic,partition,key,group,offset' -- "$@" || exit 1)
eval set -- "$TEMP"
unset TEMP

# Parse common options

while true; do
    case "$1" in
        -b|--b*)      BROKER="$2";         shift 2 ;;
        -t|--t*)      TOPIC="$2";          shift 2 ;;
        -p|--p*)      PARTITION="$2";      shift 2 ;;
        -g|--g*)      GROUPID="$2";        shift 2 ;;
        -o|--o*)      OFFSET="$2";         shift 2 ;;
        -k|--k*)      EVENTKEY="$2";       shift 2 ;;
        -a|--a*)      OFFSET='beginning';  shift ;;
        -v|--v*)      VERBOSE=1;           shift ;;
        -d|--d*)      DUMP=1;              shift ;;
        -h|--h*)      usage_exit 0  ;;
        --) shift; break ;;
        *)  err_exit "lpt1 on fire!" ;;
    esac
done

# Diagnostic output

emit "BROKER    = $BROKER"
emit "TOPIC     = $TOPIC"
[ -z "$GROUPID" ]   || emit "GROUP     = $GROUPID"
[ -z "$PARTITION" ] || emit "PARTITION = $PARTITION"
[ -z "$OFFSET" ]    || emit "OFFSET    = $OFFSET"
[ ! $VERBOSE ] || echo >&2

# Helper functions

dump_input() {
    [ $DUMP ] && jq . "$@" | tee /dev/stderr || cat "$@"
}

# Kafka (kcat) functions

kcat_send() {
    F="${1:--}" && [ "$F" = '-' ] || [ -f "$F" ] || err_exit "no such file: $F"
    dump_input "$F" | tr '\n' ' ' | kcat -P -b "$BROKER" -t "$TOPIC" ${PARTITION:+-p $PARTITION} ${GROUPID:+-G $GROUPID} ${EVENTKEY:+-k} $EVENTKEY -c 1 ${VERBOSE:+-v} "$@"
}

kcat_listen() {
    kcat -C -u -b "$BROKER" -t "$TOPIC" ${PARTITION:+-p $PARTITION} ${GROUPID:+-G $GROUPID} ${DUMP:+-J} ${VERBOSE:+-v} ${OFFSET:+ -o $OFFSET} "$@" | jq .
}

# vim: sts=4:sw=4:ai:si:et