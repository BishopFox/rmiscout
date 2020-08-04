FROM openjdk:8
COPY . /rmiscout
WORKDIR /rmiscout
RUN ./gradlew shadowJar
ENTRYPOINT ["./rmiscout.sh"]
CMD ["-h"]
