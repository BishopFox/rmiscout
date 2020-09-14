#!/bin/bash

if [[ ! -f build/libs/rmiscout-1.03.2-SNAPSHOT-all.jar ]]; then
    ./gradlew shadowJar
fi
java -jar build/libs/rmiscout-1.03.2-SNAPSHOT-all.jar "$@"
