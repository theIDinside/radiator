#!/bin/bash

sdk_url="https://github.com/matrix-org/matrix-rust-sdk.git"
kotlin_components="https://github.com/matrix-org/matrix-rust-components-kotlin.git"

usage() { 
  echo "Clones the matrix-rust-sdk repository & the kotlin components used for bindings" 1>&2;
  echo "Be sure to run this command from within the radiator directory as this store\nconfiguration files there" 1>&2;
  echo -e "Usage: \n\t$0 [-c] - clones the sdk" 1>&2; 
  echo -e "       \t$0 [-r] - clones the radiator fork of the sdk" 1>&2; 
  echo -e "       \t$0 [-b] - builds the sdk and copies files into radiator directory" 1>&2; 
  exit 1;
}

clone_into() {
  [ -d $1 ]
  res=$?
  if [ $res == 1 ]; then
    mkdir $1
  fi
  echo "git -C $1 clone $2"
  git -C $1 clone $2
}

build_sdk() {
  [ -d ./matrix-sdk/matrix-rust-components-kotlin/ ]
  res=$?
  if [ $res == 1 ]; then
    echo "you must run $0 -c first"
    usage
  fi
  [ -d ./matrix-sdk/matrix-rust-sdk/ ]
  res=$?
 
  if [ $res == 1 ]; then
    echo "you must run $0 -c first"
    usage
  fi
  ./matrix-sdk/matrix-rust-components-kotlin/scripts/build.sh -p ./matrix-sdk/matrix-rust-sdk -o ./libraries/rustsdk/matrix-rust-sdk.aar;
  exit 0;
}

while getopts "rcb" o; do
    case "${o}" in
        r)
            echo "Cloning radiator fork of rust sdk"
            clone_into ./matrix-sdk git@github.com:theIDinside/matrix-rust-sdk.git
            clone_into ./matrix-sdk git@github.com:theIDinside/matrix-rust-components-kotlin.git
            ;;
        c)
            echo "Cloning projects to ${s}"
            clone_into ./matrix-sdk "$sdk_url"
            clone_into ./matrix-sdk "$kotlin_components"
            exit
            ;;
        b)
            build_sdk
            ;;
        *)
            usage
            ;;
    esac
done
