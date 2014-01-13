IRIDIA-TAM
==========

The IRIDIA TAM (Task Abstraction Module), a device whose function is to represent tasks that have to be performed by e-puck robots in laboratory experiments.

Website: http://arnuschky.github.com/iridia-tam/
GitHub: https://github.com/arnuschky/iridia-tam/
Repository: http://arnuschky.github.com/iridia-tam/

Current version of the TAM: version 5, revision D.

The GitHub repository contains all files required for producing and using the TAM. In detail, the different directories contain:


3dmodel
-------

A 3D model of the TAM, created by using the free version of Google Sketchup (version 8). Models of the PCBs have been created using EagleUp (http://eagleup.wordpress.com/).

Additionally, the directory contains 2D plans of the plastic shell in PDF format. These plans were used to produce the plastic shell (machined from POM plastic).


coordinator
-----------

This directory contains the coordinator, the central software written in Java that controls all TAMs in a given experiment. The researcher can use the coordinator software to set the behavior of the TAMs, define their interrelationships and collect statistics. Due to the use of Java, the software it platform-independent.

The directory also includes a modified version of the Xbee-api, an open-source project to access Xbee modules using Java. See http://code.google.com/p/xbee-api/ for more information. The project has been adapted to use the Digimesh mesh networking prototcol. If desired, one could also use simple point-to-point communication or the more advanced Zigbee prototcol.


electronics
-----------

This directory contains the project files created using Eagle (non-free version, due to PCB size) and the Arduino libraries and firmware.

=== eagle ===

Contains the CAD files (board and schematics) for CADSoft Eagle. Additionally, a library with custom footprints can be found here.

=== arduino ===

Sourcecode of the TAM's firmware ("sketchbook" in Arduino-speech). There are also a number of libraries included on which the firmware depends. Some of these libraries have been written or modified by us:

 - ircom - library that allows to communicate between the TAM and the epuck using the proximity sensor of the epuck
 - TLC59116 - generic TLC59116 library, adapted from http://brmlab.cz/project/ledbar (CC Attribution-Noncommercial-Share Alike 3.0 Unported License)
 - Xbee - library for using Xbee modules with the Arduino by Andrew Rapp, adapted to work with Digimesh networks and software serial ports (GPL 3)

Others are 3rd party open-source projects included for convenience:

 - I2C - fast I2C library by Wayne Truchsess (LGPL) http://dsscircuits.com/articles/arduino-i2c-master-library.html


photos
------

High-res photos of the TAM version 4.



