# Multi-stage build
# Stage 1: Build the application
FROM maven:3.9.1-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:17-jdk

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/SmartLibrarySE-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8085

# Run the application
CMD ["java", "-jar", "app.jar"]
