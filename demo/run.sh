#!/bin/bash

# Start registries
orbd -ORBInitialPort 1050&
rmiregistry&

# Activation server and export object
rmid -J-Djava.security.policy=resources/policy&
sleep 2
java -classpath . -Djava.rmi.server.codebase=file:$PWD \
    -Djava.security.policy=resources/policy \
    com.bishopfox.example.ActivationServer

# Start Servers
java -classpath . -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory \
    -Djava.naming.provider.url=iiop://localhost:1050 \
    com.bishopfox.example.CorbaServer&
java -classpath . -Djava.rmi.server.codebase=file:$PWD \
    -Djava.rmi.server.hostname=127.0.0.1 \
    com.bishopfox.example.PlainServer&
java -classpath . -Djava.rmi.server.codebase=file:$PWD \
    -Djava.security.policy=resources/policy \
    -Djavax.net.ssl.keyStore=resources/selfsigned.jks \
    -Djavax.net.ssl.keyStorePassword=test123 \
    com.bishopfox.example.SSLServer
