FROM eclipse-temurin:21.0.9_10-jdk-jammy AS build
WORKDIR /build
COPY backend/ ./
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -ntp -pl ${SERVICE} -am -DskipTests package && cp ${SERVICE}/target/*-SNAPSHOT.jar /tmp/app.jar

FROM eclipse-temurin:21.0.9_10-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
RUN mkdir -p /app/keys && chown -R app /app
COPY --from=build /tmp/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
