#!/bin/bash
# Run this script from polarize.
RHSM_QE=/home/stoner/Projects/rhsm-qe
OWD=`pwd`
gradle clean
gradle pP
gradle publishToMavenLocal
pushd $RHSM_QE

java -cp `lein classpath` org.testng.TestNG \
-reporter com.github.redhatqe.polarize.junitreporter.XUnitReporter \
src/main/resources/suites/test-suite.xml