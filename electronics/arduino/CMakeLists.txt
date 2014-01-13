#=============================================================================#
# Author: Arne Brutschy                                                       #
# Date:   2.4.2013                                                            #
# Description: IRIDIA TAM Cmake file                                          #
#=============================================================================#


# NOTE: you MUST set your Arduino SDK path at the end of this file 
set(CMAKE_TOOLCHAIN_FILE cmake/ArduinoToolchain.cmake) # Arduino Toolchain

cmake_minimum_required(VERSION 2.8)

project(ArduinoExample C CXX)

register_hardware_platform(${CMAKE_CURRENT_SOURCE_DIR}/hardware/iridia-tam)
set(ARDUINO_DEFAULT_BOARD iridia_tam)

#print_board_list()
#print_programmer_list()

set(ARDUINO_DEFAULT_PORT /dev/ttyUSB0)
#set(ARDUINO_DEFAULT_PROGRAMMER avrispmkii)

generate_arduino_firmware(iridia_tam_firmware SKETCH "sketchbooks/v5_rD/firmware/firmware.ino")

# tests
generate_arduino_firmware(test_barriers   SKETCH "sketchbooks/v5_rD/tests/test_barriers")
generate_arduino_firmware(test_basics     SKETCH "sketchbooks/v5_rD/tests/test_basics")
generate_arduino_firmware(test_ircom      SKETCH "sketchbooks/v5_rD/tests/test_ircom")
generate_arduino_firmware(test_leds       SKETCH "sketchbooks/v5_rD/tests/test_leds")
generate_arduino_firmware(test_production SKETCH "sketchbooks/v5_rD/tests/test_production")
generate_arduino_firmware(test_proximity  SKETCH "sketchbooks/v5_rD/tests/test_proximity")
generate_arduino_firmware(test_voltage    SKETCH "sketchbooks/v5_rD/tests/test_voltage")
generate_arduino_firmware(test_xbee       SKETCH "sketchbooks/v5_rD/tests/test_xbee")

