# Etapa 1: Compilar el código usando Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copiar el archivo pom y descargar dependencias (optimiza la caché de Docker)
COPY pom.xml .
# Copiar el código fuente
COPY src ./src

# Compilar evitando correr los tests para hacer el build más rápido
RUN mvn clean compile -DskipTests

# Etapa 2: Imagen ligera para ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar solo las clases compiladas desde la etapa anterior
COPY --from=builder /app/target/classes /app/classes

# Exponer el puerto del servidor HTTP
EXPOSE 8080

# Iniciar el servidor
ENTRYPOINT ["java", "-cp", "classes", "com.iol.ratelimiter.RateLimiterHttpServer"]
