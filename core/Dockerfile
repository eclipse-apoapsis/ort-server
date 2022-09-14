# syntax=docker/dockerfile:1.3

FROM azul/zulu-openjdk-alpine:17-latest as build

WORKDIR /usr/local/src/ort-server
COPY . .

RUN --mount=type=cache,target=/tmp/.gradle/ \
    export GRADLE_USER_HOME=/tmp/.gradle/ && \
    ./gradlew --no-daemon :core:installDist

FROM azul/zulu-openjdk-alpine:17-latest as run

COPY --from=build /usr/local/src/ort-server/core/build/install/core /app/

EXPOSE 8080:8080

WORKDIR /app/bin
CMD ["./core"]
