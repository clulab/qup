#!/bin/bash

if [ "$EUID" -ne 0 ]
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
cp qup.jar /usr/local/qup/qup.jar

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
echo ""
echo "Getting started in 2 minutes:"
echo ""
echo "To run an example job:"
echo "1. sudo systemctl start qup.service"
echo "2. sudo qadduser <yourusername> 1"
echo "3. sudo apt-get install stress"
echo "4. qsub pbs-examples/example-1min.pbs"
echo ""
echo "See the job running using:"
echo "5. qstat"
echo ""
echo "When the job is completed, view the output:"
echo "6. cat job.1.stdout.txt"
echo "7. cat job.1.stderr.txt"
