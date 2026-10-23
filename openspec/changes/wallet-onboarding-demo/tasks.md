# Tasks

## 1. Scaffolding

- [x] 1.1 Crear el proyecto Quarkus en `backend/` con las extensiones REST, REST Client, Hibernate ORM con Panache, JDBC H2 y Hibernate Validator, y verificar que `mvn test` termina sin errores
- [x] 1.2 Crear el proyecto Angular en `frontend/` con componentes standalone y verificar que `ng build` y `ng test --watch=false` terminan sin errores
- [x] 1.3 Definir las propiedades del backend (`onboarding.mock.base-url` con el servidor de grupo de `docs/openapi.json` por defecto, `onboarding.demo.enabled`, H2 en archivo) y el `proxy.conf.json` del frontend que dirige `/api` al backend en el puerto 8080, y verificar con `mvn quarkus:dev` y `ng serve` levantados que `GET /api/config` responde desde el origen de `ng serve`
- [x] 1.4 Crear el `README.md` con instalación, ejecución de ambos proyectos (`mvn quarkus:dev` en `backend/` y `ng serve` en `frontend/`), modo demo, cómo cambiar de grupo y cómo reiniciar los datos borrando el archivo H2 con el backend detenido, y verificar que los comandos documentados funcionan tal como están escritos

## 2. Backend: cliente del mock KYC

- [x] 2.1 Implementar el cliente REST con crear, consultar y resolver verificación, y el mapeo `ACTIVE`→`APPROVED`, `PENDING`→`MANUAL_REVIEW`, `REJECTED`→`REJECTED`, y verificar con pruebas contra WireMock
- [x] 2.2 Mapear los errores 503 `KYC_UNAVAILABLE`, 409 `DECISION_CONFLICT`, 400, 429 y de red a errores del backend con código estable, y verificar con una prueba por cada caso
- [x] 2.3 Verificar con una prueba que el cuerpo enviado al mock solo contiene `documentType`, `document` y `scenario`, y que el cliente no registra el cuerpo en los logs

## 3. Backend: corridas, eventos y métricas

- [x] 3.1 Implementar las entidades `run` y `event` en H2 (archivo en ejecución, memoria en pruebas) y el registro de eventos con hora del servidor, y verificar con una prueba que los datos persisten tras reiniciar el contexto de persistencia
- [x] 3.2 Verificar con una prueba que ni los eventos ni las tablas contienen celular, nombre, fecha de nacimiento ni número de documento
- [x] 3.3 Implementar la función pura de duración del flujo, revisión KYC y activación, y verificar con pruebas de aprobación inmediata, revisión manual resuelta, rechazo, revisión pendiente y reintento tras 503
- [x] 3.4 Implementar la mediana y la exclusión de corridas sin envío aceptado, y verificar con pruebas de varias corridas, ninguna corrida y corridas abandonadas

## 4. Backend: API REST

- [x] 4.1 Implementar `GET /api/config`, crear corrida, registrar paso, consultar corrida y cerrar corrida como abandonada, y verificar con pruebas de RestAssured
- [x] 4.2 Implementar `POST /api/onboarding/runs/{id}/kyc` con revalidación de `document` y `scenario`, registro de eventos y rechazo con 409 si la corrida ya tiene una verificación aceptada, y verificar con pruebas que cubren 201, 503 con reintento exitoso, documento inválido y segundo envío
- [x] 4.3 Implementar la resolución manual `POST .../kyc/decision`, y verificar con pruebas de `ACTIVE`, `REJECTED`, decisión repetida y 409 por conflicto
- [x] 4.4 Implementar el resumen y la exportación de eventos, y verificar con pruebas que devuelven 403 con el modo demo inactivo y los datos esperados con el modo activo
- [x] 4.5 Verificar con una prueba que con el modo demo inactivo el backend fuerza el escenario `APPROVED` y responde 403 a la decisión manual sin llamar al mock

## 5. Frontend: flujo de onboarding

- [x] 5.1 Implementar los validadores de celular (9 dígitos, empieza por 9), fecha de nacimiento (válida, no futura, 18+), documento (`TEST-`) y el OTP simulado contra el código fijo `123456`, y verificar con pruebas unitarias de valores válidos, vacíos e inválidos, incluidos los límites de edad y un OTP incorrecto
- [x] 5.2 Implementar el servicio de flujo con el cliente de `/api`, el `runId` en `sessionStorage` y el estado de los pasos, y verificar con pruebas que retoma en el primer paso no completado tras una recarga simulada y que celular, nombre y fecha de nacimiento no se incluyen en ninguna petición
- [x] 5.3 Implementar el indicador de pasos y los 4 pasos con mensajes de error por campo, el aviso de datos de prueba y la ayuda del OTP, y verificar con pruebas de componentes que se identifica el paso actual, que no avanza con datos inválidos y que el error desaparece al corregir
- [x] 5.4 Implementar el envío con bloqueo del botón durante la petición y el reintento por servicio no disponible que conserva los datos, y verificar con una prueba de componente que un doble clic envía una sola petición y que el reintento no pide reingresar los datos
- [x] 5.5 Implementar las pantallas de resultado `APPROVED`, `MANUAL_REVIEW` y `REJECTED` sin operaciones financieras, y verificar con pruebas de componentes por cada estado
- [x] 5.6 Aplicar el diseño responsive y verificar manualmente con un viewport de 360 px que los 4 pasos y los resultados no tienen desplazamiento horizontal

## 6. Frontend: modo demo y resumen

- [x] 6.1 Implementar el selector de escenario y las acciones `ACTIVE` / `REJECTED` según `GET /api/config`, y verificar con pruebas de componentes que no aparecen con el modo demo inactivo
- [x] 6.2 Implementar el resumen con duración del flujo, revisión KYC, activación y resultado por corrida, la mediana frente a la referencia de 3 minutos y la exportación JSON, y verificar con pruebas de componente que no muestra métricas de MAU, CAC ni checkout

## 7. Integración

- [x] 7.1 Con `mvn quarkus:dev` y `ng serve` levantados contra el mock real, ejecutar el flujo completo con los escenarios `APPROVED`, `MANUAL_REVIEW` resuelto a `ACTIVE` y a `REJECTED`, `REJECTED` y `UNAVAILABLE` con reintento, y verificar que los resultados y tiempos mostrados coinciden con los eventos exportados
- [x] 7.2 Reiniciar el backend y verificar que las corridas y la mediana siguen disponibles
- [x] 7.3 Ejecutar `mvn verify` y `ng test --watch=false` completos y verificar que todo pasa
