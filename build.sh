#!/usr/bin/env bash
function gradle_prep {
  gradle clean
  gradle preparePublication 2> /dev/null
  gradle preparePublication
  gradle install
  gradle publishToMavenLocal
}

gradle_prep

if [ "$#" -eq 2 ]; then
  echo "Publishing to maven central"
  gradle publish
  echo "Copying jar to auto-services"
  # This is a hack.  Ideally, mavencentral will host the uberjars, but currently gradle publish does not do that
  scp ./build/libs/polarize-${1}-all.jar ${2}
fi