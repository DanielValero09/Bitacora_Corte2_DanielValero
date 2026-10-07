FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Resolver dependencias antes de copiar fuentes permite reutilizar esta capa.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S app && adduser -S -G app app

COPY --from=builder --chown=app:app /app/target/restaurante-0.0.1-SNAPSHOT.jar ./app.jar

ENV SPRING_PROFILES_ACTIVE=docker \
    SERVER_PORT=8080 \
    SSL_ENABLED=false

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
