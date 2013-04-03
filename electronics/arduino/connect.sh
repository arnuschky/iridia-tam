#!/bin/bash

source tam.conf

if [ "$1" == "reset" ]; then
  bin/ard-reset-arduino ${PORT}
fi
screen ${PORT} ${SPEED}

