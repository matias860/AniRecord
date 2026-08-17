FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx96m", "-XX:MaxMetaspaceSize=64m", "-XX:ReservedCodeCacheSize=32m", "-XX:MaxDirectMemorySize=16m", "-Xss256k", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
