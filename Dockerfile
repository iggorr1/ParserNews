FROM eclipse-temurin:24-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:24-jre

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=live
ENV JAVA_OPTS=""

RUN mkdir -p /app/data /app/output

COPY --from=build /workspace/target/*.jar /app/parsernews.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/parsernews.jar"]
