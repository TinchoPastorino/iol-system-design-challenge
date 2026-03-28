# Rate Limiter Design & Architecture

Este documento detalla la arquitectura, decisiones de diseño y compensaciones (trade-offs) tomadas en la implementación del **Rate Limiter** basado en el algoritmo **Token Bucket** (Alex Xu, *"System Design Interview"*).

---

## 🏗️ Arquitectura y Decisiones Técnicas

### 1. In-Memory vs. Distributed (Zero Latency)
- **Decisión:** Se utiliza un almacenamiento en memoria (`ConcurrentHashMap<String, Bucket>`) en lugar de Redis o una base de datos.
- **Razón:** Para este challenge, la prioridad es la **latencia mínima**. Consultar la RAM local toma nanosegundos, mientras que Redis sumaría milisegundos por el viaje de red. 
- **Trade-off:** Esta instancia es *standalone*. Si se escala a múltiples servidores, cada uno tendrá su propio contador (ver sección "Escalabilidad Futura" abajo).

### 2. Concurrencia y Thread-Safety
- **Aislamiento por Usuario:** Los límites se aislan por `userId`. No hay un bloqueo global del servidor.
- **Locking Fino:** Se usa `ConcurrentHashMap#computeIfAbsent` para la creación atómica de baldes y `synchronized` interno en la clase `Bucket` para la recarga de tokens. Esto permite que miles de usuarios sean procesados en paralelo sin interferencias.

### 3. Precisión Matemática (Anti-Drift)
- **Aritmética de Longs:** Se usa `long` para el conteo de tokens y timestamps en nanosegundos (`System.nanoTime()`). Se evita el uso de `double` para prevenir el "floating-point drift" (pérdida de precisión decimal) tras millones de operaciones.
- **Recarga Exacta:** El método `refill()` solo avanza el "último tiempo de recarga" por el tiempo exacto consumido por los tokens generados. Si sobran nanosegundos que no llegan a formar un token completo, se guardan para la próxima petición. **No se desperdicia tiempo.**

### 4. Prevención de Memory Leaks (Eviction)
- Un hilo **Daemon** (`ScheduledExecutorService`) corre cada 1 hora y elimina de la memoria los baldes de usuarios que no han tenido actividad por más de 60 minutos. 
- Esto garantiza que el servidor pueda estar prendido meses sin agotar la RAM por usuarios esporádicos.

---

## 📊 Observabilidad y Monitoreo (The "Senior" Stack)

El proyecto no es solo código; es "operable". Se implementó un stack completo de monitoreo industrial:

### 1. Métricas (Prometheus)
- El endpoint `/metrics/prometheus` expone contadores críticos:
    - `rate_limiter_allowed_total` / `rate_limiter_rejected_total`: Tasa de éxito/bloqueo.
    - `rate_limiter_latency_avg_ms`: Latencia de procesamiento pura medida en el servidor (excluye ruido de red).
    - `rate_limiter_active_users`: Cantidad de objetos Bucket en RAM.
    - **JVM Metrics**: Monitoreo de memoria Heap, Threads y estado de la KVM.

### 2. Logs Centralizados (Loki)
- Los logs se emiten en formato estructurado y son capturados por **Promtail** hacia **Loki**.
- Esto permite buscar logs de un usuario específico rápidamente en Grafana sin entrar por SSH al contenedor.
- Se usa nivel `INFO` con códigos HTTP (`200 OK`, `429 REJECTED`) para facilitar el filtrado visual.

### 3. Dashboard (Grafana)
- Un dashboard auto-provisionado muestra en tiempo real la salud del sistema. Permite detectar ataques (picos rojos) o degradación de performance (picos de latencia) al instante.

---

## 🚀 Escalabilidad Futura (System Design Interview)

Si este sistema debiera escalar para manejar **millones de requests por segundo** en IOL, el camino de evolución sería:

1.  **Load Balancer con Sticky Sessions:** Usar Consistent Hashing (por `userId` o IP) para asegurar que un usuario siempre caiga en el mismo nodo. Esto mantiene la ventaja de la latencia de RAM local sin necesidad de una base centralizada.
2.  **Capa Central de Estado (Redis):** Si la precisión absoluta entre nodos es requerida, se reemplazaría la lógica de `Bucket.java` por un script de LUA en Redis. Esto permitiría escalabilidad horizontal infinita a costa de un ligero incremento en la latencia.
3.  **Local Cache + Redis:** Un esquema híbrido donde se descuentan tokens localmente y se sincronizan en lotes (batch) con Redis para balancear precisión y velocidad.

---

## 🛠️ Verificación y Test de Carga
El sistema fue validado con:
- **Unit Tests (JUnit 5):** Verifican la matemática de los tokens y la concurrencia.
---

## 🤖 Uso de IA y Colaboración

Siguiendo las reglas del challenge, se detalla el uso de herramientas de IA durante el desarrollo:

1.  **Aceleración de Boilerplate:** Se utilizó IA (Antigravity/Claude) para generar el "esqueleto" del servidor HTTP nativo de Java y la configuración base de Docker (Prometheus/Grafana), lo cual permitió centrar el esfuerzo humano en la lógica core del algoritmo.
2.  **Validación de Corner Cases:** Se solicitó a la IA proponer casos de prueba extremos (como el *nanosecond drift*) para asegurar que la implementación del Token Bucket fuera matemáticamente robusta.
3.  **Refactorización y Pulido:** La IA asistió en la transición de un parsing manual de argumentos en Python a una implementación más profesional con `argparse`, eliminando bugs de análisis estático reportados por linters.
4.  **Pair Programming:** El proceso fue un diálogo constante entre el desarrollador (estableciendo los requerimientos y revisando cada línea de código) y la IA (actuando como asistente técnico avanzado), asegurando que ninguna pieza de código quedara sin entender o documentar.
