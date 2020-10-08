FROM openjdk:8
COPY . /demo/
WORKDIR /demo/
RUN chmod +x run.sh

# Build Code and RMI Stubs
RUN javac com/bishopfox/example/*.java
RUN rmic -d . com.bishopfox.example.ActivationImpl && \
    rmic -d . -iiop com.bishopfox.example.CorbaImpl && \
    rmic -d . com.bishopfox.example.SSLServer

ENTRYPOINT ["./run.sh"]
