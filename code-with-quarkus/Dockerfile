# =========================
# Build stage
# =========================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn package -DskipTests

# =========================
# Runtime stage
# =========================
FROM eclipse-temurin:21-jre

ARG DENO_VERSION=2.9.7
ARG YTDLP_VERSION=2026.11.23

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        curl \
        python3 \
        unzip \
        ca-certificates \
    && curl -fsSL \
        "https://github.com/denoland/deno/releases/download/v${DENO_VERSION}/deno-x86_64-unknown-linux-gnu.zip" \
        -o /tmp/deno.zip \
    && unzip /tmp/deno.zip -d /usr/local/bin \
    && chmod a+rx /usr/local/bin/deno \
    && rm /tmp/deno.zip \
    && curl -fL \
        "https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}/yt-dlp" \
        -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && mkdir -p /root/yt-dlp-plugins \
    && curl -fL \
        https://github.com/Brainicism/bgutil-ytdlp-pot-provider/releases/download/2.0.2/bgutil-ytdlp-pot-provider.zip \
        -o /root/yt-dlp-plugins/bgutil-ytdlp-pot-provider.zip \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/quarkus-app/ ./

EXPOSE 8080

ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080

CMD ["java", "-jar", "quarkus-run.jar"]
