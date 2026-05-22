FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY checkstyle.xml ./
COPY converter-service/pom.xml converter-service/pom.xml
COPY converter-service/src converter-service/src
RUN mvn -q -pl converter-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		fontconfig \
		fonts-dejavu-core \
		fonts-dejavu-extra \
		fonts-liberation2 \
		fonts-noto-cjk \
		fonts-noto-core \
	&& rm -rf /var/lib/apt/lists/* \
	&& fc-cache -f \
	&& useradd --system --create-home spring
COPY --from=build /workspace/converter-service/target/converter-service-1.0.0-SNAPSHOT.jar app.jar
COPY fonts fonts
RUN mkdir -p /app/artifacts && chown -R spring:spring /app
USER spring
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]