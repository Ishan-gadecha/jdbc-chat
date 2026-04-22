# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies
RUN mvn dependency:go-offline -B
COPY src ./src
# Build the WAR file
RUN mvn clean package -B -ntp

# Run stage
FROM tomcat:9.0-jdk21
WORKDIR /usr/local/tomcat

# Remove default tomcat applications
RUN rm -rf webapps/*

# Copy the built WAR to the ROOT application
COPY --from=build /app/target/jdbc-chat.war webapps/ROOT.war

# Create entrypoint script for Render PORT configuration
COPY deploy/render/entrypoint.sh /usr/local/tomcat/bin/entrypoint.sh
RUN chmod +x /usr/local/tomcat/bin/entrypoint.sh

# Use entrypoint
ENTRYPOINT ["/usr/local/tomcat/bin/entrypoint.sh"]
