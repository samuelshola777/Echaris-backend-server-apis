# ---- Build stage (cache-friendly) ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 1) Cache deps separately so code changes don't re-download the world
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# 2) Now copy sources
COPY src ./src

# 3) Build fat jar
RUN mvn -q -DskipTests clean package

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the fat jar (wildcard avoids hardcoding SNAPSHOT)
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar

# (Optional) allow JAVA_OPTS like memory flags
ENV JAVA_OPTS=""
EXPOSE 8080

# Run as non-root (safer)r
RUN useradd -u 1001 spring && chown -R spring:spring /app
USER 1001

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
