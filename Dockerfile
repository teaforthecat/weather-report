FROM openjdk:8

ADD target/weather-report-0.2.0-standalone.jar /srv/weather-report.jar

EXPOSE 8080

CMD ["java", "-jar", "/srv/weather-report.jar"]
