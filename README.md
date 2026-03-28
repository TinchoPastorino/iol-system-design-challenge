# System Design Implementation Challenge - Rate Limiter

Este repositorio contiene la implementación de un **Rate Limiter** de alto rendimiento basado en el algoritmo **Token Bucket**, diseñado siguiendo los conceptos del libro *“System Design Interview – An Insider’s Guide”* de Alex Xu.

## 📁 Estructura del Repositorio

Siguiendo las instrucciones del challenge, el código y la documentación principal se encuentran en la carpeta:
- **/rate-limiter**: Núcleo del proyecto (Java 17 + Maven).
  - `DESIGN.md`: Documentación de arquitectura, trade-offs y uso de AI.
  - `src/`: Código fuente y tests unitarios.
  - `monitoring/`: Archivos de configuración de Grafana, Prometheus y Loki.

---

## 🚀 Guía de Ejecución

Podés ejecutar este proyecto de tres formas distintas, según lo que quieras probar:

### 1. Con Docker Compose (Stack Completo de Monitoreo) - RECOMENDADO
Levanta el Rate Limiter junto con Prometheus, Grafana y Loki en un solo comando:
```bash
cd rate-limiter
docker-compose up -d --build
```
- **App**: [localhost:8080](http://localhost:8080)
- **Grafana**: [localhost:3000](http://localhost:3000) (User: `admin` / Pass: `admin`)
- **Prometheus**: [localhost:9090](http://localhost:9090)

### 2. Con Docker (Solo la App)
Si solo querés el contenedor de la aplicación sin el stack de métricas:
```bash
cd rate-limiter
docker build -t rate-limiter .
docker run -p 8080:8080 rate-limiter
```

### 3. Local (Solo la App con Maven)
Ideal para una revisión rápida del código sin dependencias de infraestructura:
```bash
cd rate-limiter
mvn clean compile
mvn exec:java
```

---

## 🧪 Cómo Probar el Sistema (Paso a Paso)

### 1. Prueba Manual (Navegador)
1. Abrí tu navegador en: [http://localhost:8080/allow?userId=martin](http://localhost:8080/allow?userId=martin)
2. Presioná **F5 (Refrescar)** rápidamente.
3. Verás como los primeros 5 intentos (configuración default) cargan correctamente, y a partir del 6to recibís un error **429 Too Many Requests**.

### 2. Prueba con `curl` (Terminal)
Usá el siguiente comando para ver los headers de respuesta reales:
```powershell
curl.exe -i "http://localhost:8080/allow?userId=martin"
```
Buscá los headers `X-RateLimit-*`:
- `X-RateLimit-Remaining`: Cuántos tiros te quedan antes del bloqueo.
- `Retry-After`: Cuántos segundos esperar si ya fuiste bloqueado.

### 3. Prueba de Carga Masiva (Stress Test)
Ejecutá el script de Python para ver el sistema bajo fuego:
```bash
cd rate-limiter
python load_test.py --requests 1000 --users 10
```
Al finalizar, te dará un reporte de la latencia medida directamente desde el servidor.

---

## 📊 Monitoreo y Observabilidad
Si usaste la **Opción 1 (Docker Compose)**, podés entrar al [Dashboard de Grafana](http://localhost:3000/d/rate-limiter-dashboard) para visualizar en tiempo real:
- Tasa de peticiones permitidas vs. rechazadas (429).
- Latencia de procesamiento (Avg/Max).
- Salud de la JVM (Hilos, Memoria Heap).
- Logs centralizados con Loki entrando en vivo.

---

## ⚙️ Configuración
Podés modificar el comportamiento sin tocar el código Java editando `rate-limiter/src/main/resources/config.properties`:
- `rate.limit.capacity`: Capacidad del balde (burst).
- `rate.limit.refillRate`: Tokens por segundo.

---

## 📝 Documentación de Diseño
Para una explicación técnica detallada de por qué se eligió esta arquitectura (Lazy Refill, Thread Safety, Anti-Drift Math), consultá el archivo:
👉 **[DESIGN.md](rate-limiter/DESIGN.md)**

## 🧹 Limpieza
Para detener y borrar todos los servicios :
```bash
cd rate-limiter
docker-compose down
```
