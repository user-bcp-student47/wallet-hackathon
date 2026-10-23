# Design

## Context

El repositorio solo contiene documentación y el contrato del mock ([openapi.json](../../../docs/openapi.json)); no hay código de aplicación ni `README.md`, aunque el alcance lo enlaza. Motivación y alcance: ver `proposal.md`; requisitos: ver `specs/`. El stack es Angular (frontend) y Quarkus (backend).

Restricciones del mock que condicionan el diseño:
- `POST /kyc/verifications` solo recibe `documentType` (`TEST`), `document` (`TEST-*`) y `scenario`, y no evita altas repetidas.
- La respuesta usa `status` `ACTIVE | PENDING | REJECTED`. El alcance habla de `APPROVED | MANUAL_REVIEW | REJECTED`, que corresponden al `scenario` y se muestran en la UI.
- `UNAVAILABLE` devuelve 503 `KYC_UNAVAILABLE` y no crea wallet.
- No existe OTP, celular ni eventos de onboarding en el mock. `GET /control/events` es de pagos.
- Hay un servidor por grupo (`.../groups/N`).

## Goals / Non-Goals

**Goals:**
- Demo ejecutable con una mediana real entre usuarios y tres tiempos calculados a partir de eventos con marca de tiempo del servidor.
- Lógica de validación, máquina de estados y cálculo de tiempos aislada de la UI y de los endpoints, para probarla sin navegador ni servidor.

**Non-Goals:**
- Autenticación, autorización por usuario o multi-tenencia.
- Alta disponibilidad, migraciones de base de datos de producción o despliegue en la nube.
- Internacionalización más allá del español.

## Decisions

### 1. Arquitectura: Angular SPA + Quarkus como BFF
```
 Angular (frontend/)  --/api-->  Quarkus (backend/)  --HTTP-->  mock KYC
 validaciones, OTP               REST + REST Client
 simulado, pasos UI              H2: corridas y eventos
                                 calculo de tiempos
```
El frontend solo habla con `/api`. El backend es la única vía al mock, de modo que la URL del grupo y el control de escenarios quedan en el servidor y no hay dependencia de CORS. *Alternativa:* el frontend llama al mock directamente; descartada porque obliga a guardar eventos y métricas en el cliente y a exponer la URL del grupo.

### 2. Stack
- **Backend:** Quarkus con `quarkus-rest` (REST y JSON), `quarkus-rest-client` para el mock, Hibernate ORM con Panache, `quarkus-jdbc-h2` y `quarkus-hibernate-validator`. Pruebas con JUnit, RestAssured y WireMock para simular el mock.
- **Frontend:** Angular con componentes standalone y formularios reactivos, y el runner de pruebas por defecto del CLI.
- Estructura: `backend/` y `frontend/` en la raíz.
- Ejecución de la demo: el backend se inicia con `mvn quarkus:dev` (puerto 8080 por defecto) y el frontend con `ng serve`, cuyo proxy de `/api` (`proxy.conf.json`) apunta al backend. El navegador solo habla con el origen de `ng serve`, así que no se necesita CORS. No se empaqueta Angular dentro del jar de Quarkus.

### 3. Reparto de responsabilidades
| Responsabilidad | Dónde |
|---|---|
| Validar celular, fecha de nacimiento, documento (experiencia de usuario) | Frontend |
| Verificar el OTP simulado (código fijo `123456`) | Frontend, solo simulado |
| Mantener los datos personales mientras dura la corrida | Frontend, solo en memoria |
| Revalidar `document` (`TEST-`) y `scenario` antes de reenviarlos al mock | Backend |
| Llamar al mock y mapear sus errores | Backend |
| Registrar eventos y calcular tiempos y mediana | Backend |

El OTP no pasa por el backend: es una simulación visible para el usuario y no protege nada. Que viva en el cliente evita crear un endpoint sin valor.

### 4. API del backend
Los eventos los crea el backend a partir de las llamadas, no se aceptan eventos arbitrarios del cliente.

| Método y ruta | Efecto |
|---|---|
| `GET /api/config` | Devuelve si el modo demo está activo |
| `POST /api/onboarding/runs` | Crea la corrida y registra `onboarding_started` |
| `POST /api/onboarding/runs/{id}/steps/{step}` | Registra `step_completed` (celular, OTP, datos, documento) |
| `POST /api/onboarding/runs/{id}/kyc` | Envía al mock y registra `kyc_submit_failed` o `kyc_submit_accepted`, `kyc_decided` y `wallet_activated` según el resultado |
| `GET /api/onboarding/runs/{id}` | Estado de la corrida, pasos completados, resultado y tiempos |
| `POST /api/onboarding/runs/{id}/kyc/decision` | Solo demo: resuelve `PENDING` con `ACTIVE` o `REJECTED` en el mock |
| `POST /api/onboarding/runs/{id}/close` | Cierra la corrida como `abandoned` |
| `GET /api/onboarding/summary` y `/api/onboarding/events` | Solo demo: resumen con mediana y exportación de eventos |

Errores: el 503 `KYC_UNAVAILABLE` del mock se devuelve como 503 con el mismo código estable. Los demás errores del mock (400, 409, 429 y red) se traducen a códigos del backend que el frontend convierte en mensajes accionables.

### 5. Modelo de datos y eventos en H2
Tablas `run` (id, creada, cierre, resultado, id de verificación del mock) y `event` (id, `run_id`, tipo, `at`, metadatos). `at` es la hora del servidor al recibir la llamada. H2 en modo archivo para conservar datos entre reinicios; en memoria para pruebas. Las duraciones no se guardan: se **derivan** de los eventos con una función pura, así cambiar la definición de una métrica no obliga a migrar datos. *Alternativa:* PostgreSQL; descartada porque la demo no requiere infraestructura.

Eventos: `onboarding_started`, `step_completed(step)`, `kyc_submit_failed(reason)`, `kyc_submit_accepted` (t1), `kyc_decided(status)` (t2), `wallet_activated` (t3), `flow_closed(outcome)`.

### 6. Definición de los tiempos
```
 t0 inicio --- t1 envio aceptado (201) --- t2 decision final KYC --- t3 wallet ACTIVE
 |<-- duracion del flujo (metrica) -->|
                                      |<---- revision KYC ---->|
                                      |<-------- activacion ------------>|
```
- Duración del flujo = t1 - t0. t1 es el instante en que se envía la petición que el mock acepta (201); así, el tiempo de revisión KYC de una aprobación inmediata es la latencia de esa respuesta y no 0 ms. Los reintentos tras 503 quedan dentro.
- Revisión KYC = t2 - t1. Activación = t3 - t1. En `APPROVED`, t2 y t3 coinciden con la respuesta de creación.
- `MANUAL_REVIEW` sin resolver deja t2 y t3 como pendientes. `REJECTED` deja t3 sin valor.
- La mediana usa solo corridas con t1, de todos los usuarios de la demo. Como los tiempos usan la hora del servidor, incluyen la latencia de red del cliente al backend, lo que es aceptable para la métrica de demo.

### 7. Privacidad
El celular, el nombre y la fecha de nacimiento nunca salen del navegador. Al backend solo llega `document`, que se reenvía al mock y no se guarda ni se escribe en los registros. Los eventos y la exportación no contienen datos personales. El log del cliente REST del mock no debe imprimir el cuerpo de la petición.

### 8. Modo demo por configuración del backend
Una propiedad (`onboarding.demo.enabled`) activa el selector de escenario, la decisión manual y el resumen. Con el modo apagado, el backend fuerza el escenario `APPROVED` y responde 403 a la decisión manual y a las consultas de resumen y eventos, de modo que no dependen de que la interfaz oculte los controles. El frontend consulta `GET /api/config` para decidir qué mostrar. *Alternativa:* parámetro de URL `?demo=1`, descartada porque cualquiera podría llamar a la API de decisión.

### 9. Continuidad y abandono
El frontend guarda el `runId` de la corrida activa en `sessionStorage`. Tras una recarga consulta `GET /runs/{id}` y se ubica en el primer paso no completado, ya que los datos de pasos anteriores no se guardan. El abandono lo dispara el cliente: al iniciar una nueva corrida con una anterior sin cerrar, o con la acción de reiniciar, llama a `close`. Con varios usuarios sin autenticación, "la corrida anterior" solo puede saberse desde el cliente. Una corrida en `MANUAL_REVIEW` pendiente no se considera abandonada hasta que se inicie otra.

### 10. Un solo envío aceptado por corrida
Como el mock no evita altas repetidas, el backend rechaza con 409 un nuevo envío si la corrida ya tiene un `kyc_submit_accepted`. El frontend también bloquea el botón mientras hay un envío en curso. Un reintento tras 503 sí se permite.

### 11. Validaciones del frontend
- Celular: 9 dígitos y empieza por 9.
- Fecha de nacimiento: válida, no futura y de 18 años o más. No se bloquea por superar 21 años; el rango 18-21 describe al público objetivo, no una regla de admisión.
- Documento: prefijo `TEST-`. Tipo de documento siempre `TEST`.
- OTP: se compara con el código fijo de prueba `123456`; un código incorrecto no avanza y permite reintentar sin límite de intentos.

## Risks / Trade-offs

- [Sin autenticación, cualquiera con acceso al backend puede crear corridas] → La demo es de alcance local; el modo demo apagado limita los endpoints sensibles. Se documenta como límite.
- [H2 en archivo no soporta varias instancias ni uso intensivo, y las corridas de pruebas anteriores sesgan la mediana] → Es suficiente para una demo de una sola instancia. No hay endpoint de limpieza: para empezar de cero se detiene el backend y se borra el archivo H2, y el README lo documenta. La exportación JSON permite consolidar fuera.
- [El mock puede cambiar el contrato o agotar el límite del grupo (429)] → Cliente aislado con mapeo de errores y mensaje genérico recuperable. El reinicio del grupo se hace fuera de la app.
- [Reintentos por 503 inflan la duración del flujo] → Es la definición acordada. `kyc_submit_failed` permite distinguirlos.
- [La hora del servidor incluye latencia de red] → Se documenta; es pequeña frente a una métrica de minutos.
- [Un fallo después del 201 del mock y antes de guardar el evento puede dejar la wallet creada sin `kyc_submit_accepted`] → El backend guarda el id de verificación y el evento en la misma transacción y, si falla, responde con error recuperable. Si se reintenta, puede crearse una alta repetida en el mock; se acepta como límite de la demo.
- [Un OTP simulado en el cliente no verifica titularidad real] → Es lo que pide el alcance; no se envían SMS reales.

## Open Questions

- El servidor de grupo por defecto es el grupo 5 del `openapi.json` y se cambia por configuración del backend. No hay preguntas abiertas pendientes.
