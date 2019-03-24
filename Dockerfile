FROM openjdk:8-alpine

COPY target/uberjar/kti.jar /kti/app.jar
COPY secrets/prod-config.edn /kti/prod-config.edn

EXPOSE 3000

CMD ["java", "-jar", "-Dconf=/kti/prod-config.edn", "/kti/app.jar"]
