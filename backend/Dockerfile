FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl

# Agent Java OpenTelemetry : auto-instrumentation par bytecode au demarrage
# JVM (HTTP entrant Spring MVC, JDBC, Tomcat...), zero changement de code
# applicatif. Version epinglee (jamais "latest") pour un build reproductible --
# a verifier/mettre a jour manuellement lors des montees de version :
# https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
ARG OTEL_JAVAAGENT_VERSION=2.30.0
RUN curl -fsSL -o /app/opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar"

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Valeurs de repli : si prometheus/tempo/grafana ne tournent pas (dev local
# partiel, ou build CI qui ne lance jamais le conteneur), l'agent OTel degrade
# gracieusement -- retries silencieux en arriere-plan sur echec d'export OTLP,
# jamais de crash au demarrage (meme doctrine que EnvoyeurEmailSmtp / clients
# Azure : absence de destination = log, pas d'exception). OTEL_METRICS_EXPORTER=none
# evite une double emission de metriques : Micrometer/Prometheus (actuator)
# reste la seule source de metriques, l'agent ne sert qu'aux traces.
# OTEL_EXPORTER_OTLP_PROTOCOL=grpc est explicite et necessaire : le SDK OTel
# Java (verifie en v2.30.0) ne deduit PAS le protocole gRPC du port 4317 tout
# seul -- sans cette variable, il retombe sur http/protobuf et echoue en
# silence contre le port gRPC de Tempo (log d'erreur recurrent, jamais de
# crash -- degradation gracieuse, mais des traces jamais livrees).
ENV OTEL_SERVICE_NAME=memoria-core \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317 \
    OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
    OTEL_TRACES_EXPORTER=otlp \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none \
    OTEL_TRACES_SAMPLER=parentbased_always_on

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
