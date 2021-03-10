FROM openjdk:8u212-jre-alpine
VOLUME /tmp
# timezone env with default
ENV TZ Europe/Berlin
ARG JAR_FILE
RUN apk -U --no-cache upgrade
COPY target/${JAR_FILE} dramabot.jar
ENTRYPOINT ["java", \
"-Xmx300m", \
"-jar", \
"/dramabot.jar"]
EXPOSE 8081