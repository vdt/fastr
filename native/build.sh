#! /bin/bash

# This script is for Ubuntu 12.10, 64-bit and JDK 1.7

JDK=$JAVA_HOME

gcc -O3 -msse4 -fno-strict-aliasing -fPIC -fno-omit-frame-pointer -W -Wall -Wno-unused -Wno-parentheses \
  -I $JDK/include/ -I $JDK/include/linux/ -I /usr/share/R/include -I. \
  -c r_gnur_GNUR.c
  
gcc -O3 -msse4 -fno-strict-aliasing -fPIC -fno-omit-frame-pointer -W -Wall  -Wno-unused -Wno-parentheses \
  -Wl,-soname=libgnurglue.so -static-libgcc \
  -shared -o libgnurglue.so r_gnur_GNUR.o \
  -lRmath -lR -lc

# Note the linking above
#   libRmath is before libR
#
#   normally we would just need libR, because it contains nmath as well, however libR defines
#   a uniform random number generator with seeds inter-connected to the R workspace, and this cannot 
#   be re-used without the R workspace
#
#   so by linking libRmath before libR, we get a standalone uniform random number generator from libRmath,
#   which will get preference of that from libR
#
#   TODO: the standalone version supports only one generator, will have to
#   extract the R's generator from the R code
#
#
