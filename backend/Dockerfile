# Stage 1: build the jar with Maven. Tests are skipped here; run them with ./mvnw test.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

# Stage 2: small runtime image with only the JRE and the jar.
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 township
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER township
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]