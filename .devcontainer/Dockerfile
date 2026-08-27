FROM mcr.microsoft.com/devcontainers/base:ubuntu

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_ROOT=/opt/android-sdk/ndk/28.2.13676358

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ca-certificates \
    curl \
    file \
    git \
    openjdk-21-jdk \
    protobuf-compiler \
    python3 \
    rsync \
    unzip \
    wget \
    zip \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
 && cd ${ANDROID_SDK_ROOT}/cmdline-tools \
 && wget -q https://dl.google.com/android/repository/commandlinetools-linux-6609375_latest.zip \
 && unzip -q commandlinetools-linux-6609375_latest.zip \
 && rm commandlinetools-linux-6609375_latest.zip

RUN yes Y | ${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin/sdkmanager --licenses
RUN ${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin/sdkmanager --install \
    "tools" \
    "platform-tools" \
    "platforms;android-36" \
    "ndk;28.2.13676358" \
    "build-tools;35.0.0" \
    "emulator"

USER vscode
RUN curl -fsSL https://sh.rustup.rs | bash -s -- -y --profile minimal --default-toolchain 1.95 \
 && /home/vscode/.cargo/bin/rustup target add \
    x86_64-linux-android \
    i686-linux-android \
    armv7-linux-androideabi \
    aarch64-linux-android

ENV PATH=/home/vscode/.cargo/bin:${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}
