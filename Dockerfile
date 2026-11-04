# 1. Build the jar with a full JDK
FROM eclipse-temurin:21.0.11_10-jdk AS build
WORKDIR /app

# Copy only the build setup first, so downloaded dependencies stay cached
# and are only downloaded again when these files change

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

# Copy the source and build the runnable jar (tests already run in CI)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# 2. Run the jar with a small JRE
FROM eclipse-temurin:21.0.11_10-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
