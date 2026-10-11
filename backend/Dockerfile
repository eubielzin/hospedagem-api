
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B \
    && find target -maxdepth 1 -name "*.jar" \
       ! -name "*original*" \
       ! -name "*sources*" \
       ! -name "*javadoc*" \
       -exec cp {} /app/app.jar \;

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 1001 app

WORKDIR /app

COPY --from=build --chown=app:app /app/app.jar app.jar

USER app

ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0"

EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
