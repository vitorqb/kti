FROM openjdk:8-alpine

COPY target/uberjar/kti.jar /kti/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/kti/app.jar"]
