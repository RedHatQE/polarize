#!/bin/bash
# Run this script after making any code changes to the jong project
TESTPOLARIZE=/home/stoner/Projects/testpolarize
OWD=`pwd`
pushd ..
gradle clean
gradle shadowJar
pushd $TESTPOLARIZE
gradle clean
gradle build --info
cd $OWD
