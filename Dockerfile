FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/advanced-chat-1.0-SNAPSHOT-jar-with-dependencies.jar /app/shiplink.jar

EXPOSE 5050

ENTRYPOINT ["java", "-jar", "/app/shiplink.jar"]
