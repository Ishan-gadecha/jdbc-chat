FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/advanced-chat-1.0-SNAPSHOT-jar-with-dependencies.jar /app/shiplink.jar

EXPOSE 5050

ENTRYPOINT ["java", "-jar", "/app/shiplink.jar"]
