#!/usr/bin/env bash

function builder () {
  echo "Building in $1"
  pushd $1
  gradle clean
  if [ "$2" == "true" ]; then
    gradle uploadArchives
  else
    gradle build
  fi
  popd
}

function help {
  echo "first arg is to byzantine path"
  echo "second arg is to polarize-bus"
  echo "third arg is to polarize-reporter"
  echo "optional 4th arg is whether to upload archives or not"
}

builder $1 $4
builder $2 $4
builder $3 $4
builder "$PWD" $4