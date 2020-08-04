#!/bin/bash

javac com/bishopfox/example/*.java
if [ "$1" ]; then
    echo "Starting SSL Server"
    rmic -d . com.bishopfox.example.SSLServer
    java -classpath ./ -Djava.rmi.server.codebase=file:$PWD \
        -Djava.security.policy=resources/policy \
        -Djavax.net.ssl.keyStore=resources/selfsigned.jks \
        -Djavax.net.ssl.keyStorePassword=test123 \
        com.bishopfox.example.SSLServer
else
    rmiregistry &
    PID=$!
    java -classpath ./ -Djava.rmi.server.codebase=file:$PWD com.bishopfox.example.Server
    kill $PID
fi
