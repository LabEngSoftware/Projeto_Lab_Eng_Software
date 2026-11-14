# Os testes rodam no pipeline antes do build da imagem, por isso o -DskipTests.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar /app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
