FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy

WORKDIR /app
COPY src/FibonacciServer.java /app/FibonacciServer.java

RUN javac FibonacciServer.java

EXPOSE 9090

CMD ["java", "FibonacciServer", "9090"]
