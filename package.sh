#!/bin/bash
sbt assembly
cp target/scala-2.12/qup-assembly-1.0.1.jar qup.jar
