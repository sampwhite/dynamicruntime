#!/usr/bin/env bash
# Adds a token that can be used to act as particular user or to login as that user.
# Command line is
# addToken <username> <tokenId> <tokenPassword>
SCRIPT=$(readlink -f "$0")
PATH_TO_DIR=`dirname "$SCRIPT"`

$PATH_TO_DIR/execScript.sh script.AddToken $@