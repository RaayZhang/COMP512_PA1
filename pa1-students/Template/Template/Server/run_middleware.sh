#!/usr/bin/env bash
set -e

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <flightHost> <carHost> <roomHost>" >&2
    exit 1
fi

exec java -cp . Server.RMI.RMIMiddleware "$1" "$2" "$3"
