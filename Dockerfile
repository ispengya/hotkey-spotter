FROM zookeeper:latest

WORKDIR /app
COPY hot-spotter-server/target/hot-spotter-server-0.0.1-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS="-Xms256m -Xmx2g"

EXPOSE 8888

ENTRYPOINT exec java ${JAVA_OPTS} -jar /app/app.jar
