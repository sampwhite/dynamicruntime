#!/bin/sh
# These are the commands we used to upgrade an AWS node so it can do code deploy and run the DnServer application.
sudo apt-get -y update
sudo apt-get -y install ruby
sudo apt-get -y install wget
sudo apt-get -y install daemontools
sudo apt-get -y install openjdk-11-jdk

cd /home/ubuntu
# Replace $AWS_BUCKET_NAME with AWS bucket-name that has instance (or set AWS_BUCKET_NAME in environment).
# Example:
# AWS_BUCKET_NAME=aws-codedeploy-us-east-2
# See https://docs.aws.amazon.com/codedeploy/latest/userguide/resource-kit.html#resource-kit-bucket-names
wget https://$AWS_BUCKET_NAME.s3.amazonaws.com/latest/install
chmod +x ./install
# Installs code deploy agent.
sudo ./install auto
