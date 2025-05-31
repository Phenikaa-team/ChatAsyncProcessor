FROM eclipse-temurin:17
WORKDIR /app
COPY build/libs/AsyncChatProcessor-1.0-SNAPSHOT.jar .
CMD ["java", "-jar", "AsyncChatProcessor-1.0-SNAPSHOT.jar"]
