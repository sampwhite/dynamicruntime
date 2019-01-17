#!/bin/sh
DIR=`dirname "$0"`
DN_DIR="${DIR}/../../.."
cd $DN_DIR
export DN_PROJECT_DIR=`pwd`
git pull
# Use AWS trick for getting our private IP address
export NODE_IP_ADDRESS=`curl -s http://169.254.169.254/latest/meta-data/local-ipv4`
./gradlew --console=plain execute
