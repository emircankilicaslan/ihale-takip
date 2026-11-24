FROM maven:3.9-eclipse-temurin-26 AS derle
WORKDIR /uygulama
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /uygulama
RUN useradd --system --no-create-home uygulama
COPY --from=derle /uygulama/target/ihale-takip-*.jar uygulama.jar
USER uygulama
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "uygulama.jar"]
