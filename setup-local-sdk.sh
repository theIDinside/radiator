#!/bin/bash

sdk_url="https://github.com/matrix-org/matrix-rust-sdk.git"
kotlin_components="https://github.com/matrix-org/matrix-rust-components-kotlin.git"

usage() { 
  echo "Clones the matrix-rust-sdk repository & the kotlin components used for bindings" 1>&2;
  echo "Be sure to run this command from within the radiator directory as this store\nconfiguration files there" 1>&2;
  echo -e "Usage: \n\t$0 [-c] - clones the sdk" 1>&2; 
  echo -e "       \t$0 [-f] - clones the radiator fork of the sdk" 1>&2; 
  echo -e "       \t$0 [-b] - builds the sdk and copies files into radiator directory" 1>&2; 
  echo -e "       \t$0 [-r] - release-builds the sdk and copies files into radiator directory" 1>&2; 
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

has_dependency() {
  command -v $1 &2>/dev/null
  res=$?
  return $res
}

has_cargo_dep() {
  cargo --list | grep $1
  res=$?
  return $res
}

ask() {
  echo $1
  echo "Type y for yes and n for no (case senstitive. input must either be n or y, exactly)"
  while read ans; do
    if [ $ans == 'y' ]; then
      return 0
    elif [ $ans == 'n' ]; then
      echo "Exiting. Toolchain verification aborted"
      exit 1
    fi
    echo "You must type either y or n"
  done
}

# Verifies the toolchain requirements & asks the user permission to install them if the script
# can't find them installed on the system.
verify_toolchain() {
  # Check if protobuf compiler is installed, ask to install if not
  if ! [ "$(has_dependency protoc)" ]; then
    ask "Protoc is not installed. Install it now?"
    res=$(command -v apt-get)
    if [ $res == 0 ]; then
      sudo apt-get install protoc
    else
      sudo dnf install protoc
    fi
  fi

  # Check if rust toolchain is installed (cargo & rust compiler) 
  # ask user if we should install otherwise
  if ! [ "$(has_dependency cargo)" ]; then
    echo "Cargo is not installed. You need to first install rustup"
    ask "Install rust toolchain now?"
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
    installedOk=$?
    if [ $installedOk != 0 ]; then
      echo "Installing cargo toolchain failed?"
      exit 1
    fi
  fi

  # Check if cargo-ndk is installed (cargo installed) - otherwise ask 
  # to install
  if ! [ $(has_cargo_dep ndk) ]; then
      ask "cargo-ndk not installed. Install now?"
      cargo install cargo-ndk
  fi
  # Add all Android targets for cargo
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
}

build_sdk() {
  verify_toolchain
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
  ./matrix-sdk/matrix-rust-components-kotlin/scripts/build.sh $1 -p ./matrix-sdk/matrix-rust-sdk -o ./libraries/rustsdk/matrix-rust-sdk.aar;
  exit 0;
}

while getopts "fcbr" o; do
    case "${o}" in
        f)
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
        r)
            build_sdk "-r"
            ;;
        b)
            build_sdk
            ;;
        *)
            usage
            ;;
    esac
done

usage
