# Stage 1: build — uses the full Maven + JDK image to compile and package the jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom.xml first so dependency resolution is cached as a separate layer;
# the src copy only invalidates the cache when source files change.
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: run — uses a minimal JRE image to keep the final image small
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
