#!/bin/bash
# Run this script after making any code changes to the polarize project
TESTPOLARIZE=/home/stoner/Projects/testpolarize
OWD=`pwd`
echo $OWD
pushd ..
gradle clean
gradle shadowJar
pushd $TESTPOLARIZE
gradle clean
gradle build --info
cd $OWD
