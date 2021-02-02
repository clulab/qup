#!/bin/bash

mkdir /var/log/if [ "$EUID" -ne 0 ]
  then echo "Please run installation script as root"
  exit
fi

# Create directories
mkdir -p /var/log/qup/
mkdir -p /etc/qup/
mkdir -p /usr/local/qup/
mkdir -p /usr/local/qup/jobscripts/
mkdir -p /usr/local/bin/

# Copy configuration file
cp configuration.properties /etc/qup/

# Copy main JAR
cp out/artifacts/microjobscheduler_jar/microjobscheduler.jar /usr/local/qup/qup.jar

# Copy user tools to accessible location
chmod +x usertools/*
cp usertools/* /usr/local/bin/

# Copy service script
chmod +x service/qup.sh
cp service/qup.sh /usr/local/bin/

# Copy systemd .service file
cp service/qup.service /etc/systemd/system/


# User message
echo "To start the qup service, use 'sudo systemctl start qup.service'"
echo "To stop the qup service, use 'sudo systemctl stop qup.service'"
