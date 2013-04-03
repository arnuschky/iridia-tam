#!/bin/bash

source tam.conf

mkdir -p build &> /dev/null
cd build
PWD=`pwd`
if [ "$1" == "clean" ] && [ `basename ${PWD}` == "build" ]; then
  shift
  echo "Cleaning "`pwd`
  rm -rf *
fi

cmake .. || exit 1

make ${SKETCH} || exit 1

if [ "$1" == "upload" ]; then
  make ${SKETCH}-upload || exit 1

  cd ..

  bin/ard-reset-arduino ${PORT}
  screen ${PORT} ${SPEED}
fi

