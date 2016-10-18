#!/bin/bash
# Run this script after making any code changes to the rhsm-qe project
# This script merely compiles the rhsm-qe project.  For this to work, the project.clj must include the polarize
# jars.  Moreover, for debug purposes, make sure that the jars have been installed to the maven repo.  This is
# accomplished with the gradle publishToMavenLocal
TESTPOLARIZE=/home/stoner/Projects/rhsm-qe
OWD=`pwd`
echo $OWD
pushd ..
gradle clean
gradle pP
gradle publishToMavenLocal
pushd $TESTPOLARIZE
lein clean
lein compile
cd $OWD