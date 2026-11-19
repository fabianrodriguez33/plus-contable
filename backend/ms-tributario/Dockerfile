# Imagen de ms-tributario para el despliegue gratis (Render, 512 MB de RAM). Contexto de construccion: backend/ms-tributario
# Construir:  docker build -t ms-tributario backend/ms-tributario
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# mvnw puede venir con fin de linea de Windows: se normaliza para el shell de Linux
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package \
    && cp "$(ls target/*.jar | grep -v '\.original$' | head -1)" app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /data/uploads && chown app:app /data/uploads
WORKDIR /app
COPY --from=build --chown=app:app /app/app.jar app.jar
USER app
# Memoria para 512 MB en total: heap 280 MB + metaspace 110 MB + codigo 40 MB + pilas, deja ~60 MB al sistema.
# SerialGC y el compilador C1 ahorran memoria y arrancan mas rapido que los valores por defecto. Se cambia con JAVA_OPTS.
ENV JAVA_OPTS="-Xms64m -Xmx280m -XX:MaxMetaspaceSize=110m -XX:ReservedCodeCacheSize=40m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
# El puerto sale de la variable PORT (Render la define); sin ella usa 8082
ENV PORT=8082
# Comprobantes y documentos: sin disco persistente (plan gratis) se pierden al reiniciar; ver docs/DESPLIEGUE.md
ENV STORAGE_DIR=/data/uploads
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
