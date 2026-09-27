# --- Build stage: compile and package the Ktor app into a runnable distribution ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon installDist

# --- Run stage: slim JRE with just the built distribution ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/questly-backend/ /app/
# Hosts (Render/Railway/Fly) inject PORT; the app reads it (defaults to 8080).
EXPOSE 8080
CMD ["/app/bin/questly-backend"]
