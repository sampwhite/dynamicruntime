#!/bin/sh
SERVICE_NAME=DnServer
SCRIPT=$(readlink -f "$0")
PATH_TO_DIR=`dirname "$SCRIPT"`


DN_DIR="${PATH_TO_DIR}/../../../.."
cd $DN_DIR
export DN_PROJECT_DIR=`pwd`
PID_PATH_NAME=$DN_PROJECT_DIR/dynapp.pid

if [ -f $DN_PROJECT_DIR/gradlew ]; then
    GRADLEW_DIR=$DN_PROJECT_DIR
else
    echo "Using dynamicruntime directory, consider putting a settings.gradle and gradlew into parent directory"
    GRADLEW_DIR=$DN_PROJECT_DIR/dynamicruntime
fi

start() {
    cd $DN_PROJECT_DIR/dynamicruntime
    git pull
    cd $GRADLEW_DIR

    # Use AWS trick for getting our private IP address
    export NODE_IP_ADDRESS=`curl -s http://169.254.169.254/latest/meta-data/local-ipv4`

    nohup ./gradlew --console=plain execute 2>&1 | multilog t s1048576 n3 ./logs &
    jobs -p  > $PID_PATH_NAME

    #nohup $EXE_PATH 2>&1 | multilog t s1048576 n3 ./logs &
    # | multilog t s1048576 n3 ./logs
    # echo $! > $PID_PATH_NAME
}

detach() {
    # $PATH_TO_DIR/removeFromCluster.sh
    echo "Skipping detach"
}

case $1 in
    start)
        echo "Starting $SERVICE_NAME ..."
        if [ ! -f $PID_PATH_NAME ]; then
            # $EXE_PATH
            start
            echo "$SERVICE_NAME started ..."
        else
            echo "$SERVICE_NAME is already running ..."
        fi
    ;;
    stop)
        if [ -f $PID_PATH_NAME ]; then
            PID=$(cat $PID_PATH_NAME);
            echo "$SERVICE_NAME stopping ..."
            detach
            kill $PID;
            echo "$SERVICE_NAME stopped ..."
            rm $PID_PATH_NAME
        else
            echo "$SERVICE_NAME is not running ..."
        fi
    ;;
    restart)
        if [ -f $PID_PATH_NAME ]; then
            PID=$(cat $PID_PATH_NAME);
            echo "$SERVICE_NAME stopping ...";
            detach
            kill $PID;
            echo "$SERVICE_NAME stopped ...";
            rm $PID_PATH_NAME
            echo "$SERVICE_NAME starting ..."
            start
            echo "$SERVICE_NAME started ..."
        else
            echo "$SERVICE_NAME is not running ..."
        fi
    ;;
esac
