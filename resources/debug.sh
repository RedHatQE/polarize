#!/bin/bash
# Run this script after making any code changes to the jong project
TESTJONG=/home/stoner/Projects/testjong
OWD=`pwd`
pushd ..
gradle clean
gradle shadowJar
pushd $TESTJONG
gradle clean
gradle build --info
cd $OWD