FROM eclipse-temurin:17-jdk
WORKDIR /app

# Install Gradle
RUN apt-get update && apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-8.10.2-bin.zip && \
    unzip gradle-8.10.2-bin.zip && \
    mv gradle-8.10.2 /opt/gradle && \
    rm gradle-8.10.2-bin.zip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Add Gradle to PATH
ENV PATH="/opt/gradle/bin:${PATH}"

# Copy build files and card definitions
COPY build.gradle settings.gradle ./
COPY gameplay*definition.txt ./
COPY src/ ./src/

# Build the application
RUN gradle clean build -x test

# Run the application
CMD ["java","-jar","build/libs/office-duel-engine.jar"]
