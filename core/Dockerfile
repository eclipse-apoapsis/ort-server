# syntax=docker/dockerfile:1.3

FROM eclipse-temurin:17-alpine as build

WORKDIR /usr/local/src/ort-server
COPY . .

RUN --mount=type=cache,target=/tmp/.gradle/ \
    export GRADLE_USER_HOME=/tmp/.gradle/ && \
    ./gradlew --no-daemon :core:installDist

FROM eclipse-temurin:17-jre-alpine as run

COPY --from=build /usr/local/src/ort-server/core/build/install/core /app/

EXPOSE 8080:8080

WORKDIR /app/bin
CMD ["./core"]
