#!/bin/bash
# 1050: Corba/RMI-IIOP
# 1098: Activation System Daemon (rmid)/accessed from 1099 "ActivationServer"
# 1099: rmiregistry for "plaintest" and "ActivationServer"
# 1099: RMI-SSL
# 1111: "plaintest" Localhost bound RMI Server (localhost-bypass test)
#       accessed from 1099
docker build . -t "rmiscout-demo" && \
    docker run \
    -p 1050:1050 \
    -p 1098:1098 \
    -p 1099:1099 \
    -p 1100:1100 \
    -p 172.17.0.1:1111:1111 \
    -it rmiscout-demo
