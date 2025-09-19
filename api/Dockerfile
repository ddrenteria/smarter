FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy Gradle wrapper and build files first for better caching
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew clean build -x test

# Run the application
CMD ["java","-jar","build/libs/office-duel-engine.jar"]
