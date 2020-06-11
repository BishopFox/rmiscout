#!/bin/bash

if [[ ! -f build/libs/rmiscout-1.01-SNAPSHOT-all.jar ]]; then
    ./gradlew shadowJar
fi
java -jar build/libs/rmiscout-1.01-SNAPSHOT-all.jar "$@"
