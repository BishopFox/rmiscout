#!/bin/bash

rmiregistry &
PID=$!

javac *.java && java -classpath ./ -Djava.rmi.server.codebase=file:$PWD Server
kill $PID
