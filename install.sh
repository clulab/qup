#!/bin/bash
#/etc/qup/configuration.properties
#/etc/qup/serverpasswords
#/etc/qup/statisticsfile

mkdir /var/log/
mkdir /var/log/qup/
mkdir /etc/qup/
mkdir /usr/local/
mkdir /usr/local/qup/
mkdir /usr/local/qup/jobscripts/
mkdir /usr/local/bin/

cp configuration.properties /etc/qup/
cp out/artifacts/microjobscheduler_jar/microjobscheduler.jar /usr/local/qup/qup.jar
cp usertools/* /usr/local/bin/
