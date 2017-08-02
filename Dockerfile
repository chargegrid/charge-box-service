FROM openjdk:8-jre-alpine
COPY target/uberjar/charge-box-service.jar /app/charge-box-service.jar
WORKDIR /app
EXPOSE 8083
CMD ["java", "-jar", "charge-box-service.jar"]
