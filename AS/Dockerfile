FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy

WORKDIR /app
COPY src/AuthoritativeServer.java /app/AuthoritativeServer.java

RUN javac AuthoritativeServer.java

EXPOSE 53533/udp

CMD ["java", "AuthoritativeServer", "53533"]
