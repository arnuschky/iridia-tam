#=============================================================================#
# Author: QueezyTheGreat                                                      #
# Date:   26.04.2011                                                          #
#                                                                             #
# Description: Arduino CMake example                                          #
#                                                                             #
#=============================================================================#
set(CMAKE_TOOLCHAIN_FILE cmake/ArduinoToolchain.cmake) # Arduino Toolchain


cmake_minimum_required(VERSION 2.8)
#====================================================================#
#  Setup Project                                                     #
#====================================================================#
project(ArduinoExample C CXX)

#print_board_list()
#print_programmer_list()

set(ARDUINO_DEFAULT_BOARD atmega328)
set(ARDUINO_DEFAULT_PORT /dev/ttyUSB0)

generate_arduino_firmware(iridia_tam_ircom
    SKETCH "sketchbooks/iridia_tam_ircom")

generate_arduino_firmware(iridia_tam_ircom_arne
    SKETCH "sketchbooks/iridia_tam_ircom_arne")

generate_arduino_firmware(iridia_tam_v4_rB_xbee
    SKETCH "sketchbooks/iridia_tam_v4_rB_xbee")

generate_arduino_firmware(iridia_tam_v4_rB_pstest
    SKETCH "sketchbooks/iridia_tam_v4_rB_pstest")

