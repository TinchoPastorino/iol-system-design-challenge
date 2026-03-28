# Rate Limiter Challenge

Implementación de un **Rate Limiter** de alto rendimiento basado en el algoritmo **Token Bucket**. Diseñado en **Java 17 puro** para máxima eficiencia, sin el overhead de frameworks externos como Spring Boot o bases de datos como Redis.

---

## 📋 Requisitos Previos
- **Docker** y **Docker Compose** (Indispensable para el stack de monitoreo).
- **Python 3** (Opcional, para el script de prueba de carga).
- **Java 17** y **Maven** (Opcional, solo si deseas compilarlo fuera de Docker).

---

## 🏁 Inicio Rápido (Prueba End-to-End)

### 1. Ejecutar solo la App
```bash
docker build -t rate-limiter .
docker run -p 8080:8080 rate-limiter
```

> [!TIP]
> **Configuración**: El sistema usa `src/main/resources/config.properties` por defecto. Podés pisarla montando un archivo externo:
> `docker run -p 8080:8080 -v ${PWD}/config.properties:/app/config.properties rate-limiter`

### 2. Ejecutar con Monitoreo (Grafana + Prometheus)
Para levantar el Rate Limiter junto con todo el ecosistema de monitoreo (Prometheus, Grafana, Loki), ejecutá:

```bash
docker-compose up -d --build
```

### Servicios Disponibles:
| Servicio | URL | Descripción |
|---|---|---|
| **Rate Limiter APP** | [localhost:8080](http://localhost:8080) | El core del sistema |
| **Grafana Dashboard** | [localhost:3000](http://localhost:3000) | Visualización en tiempo real (User: `admin` / Pass: `admin`) |
| **Prometheus** | [localhost:9090](http://localhost:9090) | Base de datos de métricas |

---

## 🧪 Cómo Probar el Sistema (Paso a Paso)

### 1. Prueba Manual (Navegador)
1. Abrí tu navegador en: [http://localhost:8080/allow?userId=martin](http://localhost:8080/allow?userId=martin)
2. Presioná **F5 (Refrescar)** rápidamente.
3. Verás como los primeros 5 intentos (default) cargan correctamente, y a partir del 6to recibís un error **429 Too Many Requests**.

### 2. Prueba de "Caja Negra" (Terminal)
Usá el siguiente comando para ver los headers de respuesta reales:
```powershell
curl.exe -i "http://localhost:8080/allow?userId=martin"
```
Buscá los headers `X-RateLimit-*`:
- `X-RateLimit-Remaining`: Te dice cuántos tiros te quedan antes del bloqueo.
- `Retry-After`: Te dice cuántos segundos esperar si ya fuiste bloqueado.

### 3. Prueba de Carga Masiva (Stress Test)
Ejecutá el script de Python para ver el sistema bajo fuego:
```bash
python load_test.py --requests 1000 --users 10
```
Este script simulará 10 usuarios concurrentes tirando 1000 pedidos. Al finalizar, te dará un reporte de la latencia medida directamente desde el servidor.

---

## 📊 Monitoreo y Observabilidad

### Grafana Dashboard
Accedé a [http://localhost:3000/d/rate-limiter-dashboard](http://localhost:3000/d/rate-limiter-dashboard) para ver:
- **Requests Rate**: Picos verdes y rojos que muestran la actividad en vivo.
- **Allowed vs Rejected**: Gráfico de torta con porcentajes de bloqueo.
- **Active Users**: Cantidad de usuarios únicos que el sistema tiene en memoria.
- **JVM Health**: Uso de memoria RAM (Heap) y cantidad de hilos de la Java Virtual Machine.
- **Live Logs**: En la parte inferior verás los logs de `200 OK` y `429 REJECTED` entrando en tiempo real.

---

## ⚙️ Configuración y Personalización

Podés modificar el comportamiento del Rate Limiter sin tocar el código Java editando el archivo `config.properties`:

```properties
rate.limit.capacity=5        # Cuántos pedidos permitís en una ráfaga (burst)
rate.limit.refillRate=1.0    # Cuántos nuevos pedidos permitís por cada segundo
server.port=8080             # Puerto donde escucha la app
```
*Nota: Después de cambiar este archivo, debés reiniciar con `docker-compose up -d --build`.*

---

## 📡 Endpoints de la API

- `GET /allow?userId={id}`: El endpoint principal para validar tráfico.
- `GET /metrics`: Reporte rápido de estadísticas en formato JSON.
- `GET /metrics/prometheus`: Exposición de métricas para scraping automático.

---

## 🧹 Limpieza

Para detener y borrar todos los servicios, liberando la memoria de tu PC:
```bash
docker-compose down
```

---

## 📂 Organización del Código
- `src/main/java`: El código core (Token Bucket, Server, Metrics).
- `src/test/java`: Tests de unidad e integración (JUnit 5).
- `monitoring/`: Archivos de configuración de Grafana, Prometheus y Loki.
- `DESIGN.md`: Explicación técnica detallada de por qué se eligió esta arquitectura.
