#!/usr/bin/env bash
gradle clean
gradle preparePublication 2> /dev/null
gradle preparePublication
gradle install
gradle publishToMavenLocal
gradle publish

# This is a hack.  Ideally, mavencentral will host the uberjars, but currently gradle publish does not do that
#scp ./build/libs/polarize-${1}-all.jar ${2}