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
# Force IPv4. Render containers have no working IPv6 route, but the JVM/Ktor client
# prefers AAAA records for hosts like overpass-api.de -> "connection refused"/timeouts
# on every outbound call. preferIPv4Stack makes the client dial reachable IPv4 addresses.
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
CMD ["/app/bin/questly-backend"]
