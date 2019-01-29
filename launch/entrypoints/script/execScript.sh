#!/bin/bash
SCRIPT=$(readlink -f "$0")
PATH_TO_DIR=`dirname "$SCRIPT"`


DN_DIR="${PATH_TO_DIR}/../../../.."
cd $DN_DIR
export DN_PROJECT_DIR=`pwd`

for var in "$@"
do
	if [ -z $mainClass ]; then
	    mainClass="$var"
    elif [ -z $result ]; then
        echo "Setting result"
        result=$(printf "%b" "$var")
    else
        result+="^"
        result+=$(printf "%b" "$var")
    fi
done
if [ ! -z $mainClass ]; then
    ./gradlew --console plain execute -PmainClass=$mainClass -PdnArgs=$result
else
    echo "Script requires a mainClass to be specified"
fi
