FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY src/UserServer.java /app/UserServer.java
RUN javac UserServer.java
EXPOSE 8080
CMD ["java", "UserServer"]
