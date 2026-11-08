FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY pppp-emulator/pom.xml pppp-emulator/
COPY pppp-core/pom.xml pppp-core/
COPY pppp-app/pom.xml pppp-app/
RUN mvn -q -B dependency:go-offline -DexcludeArtifactIds=pppp-emulator,pppp-core || true
COPY . .
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/pppp-app/target/pppp-app-*.jar app.jar
ENV PPPP_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
