#!/bin/bash

export LC_ALL="C"
set -euo pipefail

USAGE="${USAGE:-}

  COMMON OPTIONS
   -b,--broker=BROKER   Set the broker host:port [$BROKER]
   -t,--topic=TOPIC     Set the topic [$TOPIC]
   -g,--group=GROUPID   Group ID of client [$GROUPID]
   -p,--partition=PART  Set the partition [$PART]
   -o,--offset=OFFSET   Start reading at offset [$OFFSET]
   -k,--key=KAFKAKEY    Set the kafka event key [$KAFKAKEY]
   -a,--all             Read the topic from the beginning
   -d,--dump            Write input to stderr / full output
   -r,--raw             Do not filter output through JQ
   -n,--dry-run         Do everything except the real thing
   -v,--verbose         Display diagnostic output
   -h,--help            Display usage information

  All CAPITALISED options can also be passed as environment vars
"
# Note how the usage_exit function pulls in the including script's USAGE string

usage_exit() { echo "Usage: ${0##*/} [-adrnvh] [-b BROKER ] [-t TOPIC] [-g GROUPID] [-p PART] [-o OFFSET] [-k KAFKAKEY] ${USAGE}" >&2; exit ${1:-1}; }

# Check common options

TEMP=$(getopt -n "${0##*/}" -o 'adrnvhb:t:g:p:o:k:' -l 'all,dump,raw,dry-run,dry-run,verbose,help,broker:,topic:,group:,partition:,offset:,key:' -- "$@" || exit 1)
eval set -- "$TEMP"
unset TEMP

# Parse common options

while true; do
    case "$1" in
        -b|--b*)    BROKER="$2";          shift 2 ;;
        -t|--t*)    TOPIC="$2";           shift 2 ;;
        -g|--g*)    GROUPID="$2";         shift 2 ;;
        -p|--p*)    PART="$2";            shift 2 ;;
        -o|--o*)    OFFSET="$2";          shift 2 ;;
        -a|--a*)    OFFSET='beginning';   shift ;;
        -k|--k*)    KAFKAKEY="$2";        shift 2 ;;
        -d|--du*)   DUMP=1;               shift ;;
        -r|--r*)    RAWOUT=1;             shift ;;
        -n|--dr*)   NOT_REALLY=1; DUMP=1; shift ;;
        -v|--v*)    VERBOSE=1;            shift ;;
        -h|--h*)    usage_exit 0  ;;
        --) shift; break ;;
        *)  err_exit "lpt1 on fire!" ;;
    esac
done

# Check required params

[ $NOT_REALLY ] || [ -n "$BROKER" ] || err_exit "BROKER must be specified; set DEFAULT_BROKER in lib/defaults"
[ $NOT_REALLY ] || [ -n "$TOPIC" ] || err_exit "TOPIC must be specified; set DEFAULT_TOPIC in lib/defaults"

# Resolve necessary tools

KCAT="$(which kcat 2>/dev/null)" || KCAT="$(which kafkacat 2>/dev/null)" || err_exit "command not found: kcat or kafkacat (do: apt install)"
JQ="$(which jq 2>/dev/null)" || err_exit "command not found: jq (do: apt install jq)"

# Input and output filters

dump_diags() {
    emit "BROKER    = $BROKER"
    emit "TOPIC     = $TOPIC"
    [ -z "$GROUPID" ]   || emit "GROUPID   = $GROUPID"
    [ -z "$PART" ]      || emit "PART      = $PART"
    [ -z "$OFFSET" ]    || emit "OFFSET    = $OFFSET"
    [ -z "$KAFKAKEY" ]  || emit "KAFKAKEY  = $KAFKAKEY"
}

json_filter() {
    [ $RAWOUT ] && cat "$@" || "$JQ" . "$@"
}

dump_filter() {
    [ $DUMP ] && json_filter "$@" | tee /dev/stderr || cat "$@"
}

# Kafka (kcat) functions

kcat_send() {
    F="${1:--}" && [ "$F" = '-' ] || [ -f "$F" ] || err_exit "no such file: $F"
    dump_filter "$F" | tr '\n' ' ' | if [ $NOT_REALLY ]; then cat >/dev/null; else
        "$KCAT" -P -b "$BROKER" -t "$TOPIC" ${PART:+-p $PART} ${GROUPID:+-G $GROUPID} ${KAFKAKEY:+-k} $KAFKAKEY -c 1 ${VERBOSE:+-v} "$@"
    fi
}

kcat_listen() {
    [ $NOT_REALLY ] && emit "not going to listen (dry run)" && exit 0 || true
    "$KCAT" -C -u -b "$BROKER" -t "$TOPIC" ${PART:+-p $PART} ${GROUPID:+-G $GROUPID} ${DUMP:+-J} ${VERBOSE:+-v} ${OFFSET:+ -o $OFFSET} "$@" | json_filter
}

# vim: sts=4:sw=4:ai:si:et
