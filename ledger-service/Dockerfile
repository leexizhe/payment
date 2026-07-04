# Build stage: resolve dependencies first so source edits don't bust the dep cache layer
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --quiet dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode --quiet -DskipTests package

# Runtime stage: JRE only, non-root
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home ledger
USER ledger
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
