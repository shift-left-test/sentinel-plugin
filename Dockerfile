# syntax=docker/dockerfile:1

# ── dev: Ubuntu 24.04 development environment ───────────────────
FROM ubuntu:24.04 AS dev

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless maven git && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -q

CMD ["bash"]

# ── build: compile, test, package ────────────────────────────────
FROM dev AS build

ARG MAVEN_GOALS="clean verify"
ARG MAVEN_OPTS=""

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn ${MAVEN_GOALS} ${MAVEN_OPTS} -B

# ── package: .hpi artifact only ──────────────────────────────────
FROM ubuntu:24.04 AS package

COPY --from=build /workspace/target/*.hpi /opt/sentinel.hpi
