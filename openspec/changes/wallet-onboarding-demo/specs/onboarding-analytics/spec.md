# Spec Delta

## Purpose

Define los eventos de onboarding y las duraciones derivadas, para calcular la duración del flujo del usuario por separado del tiempo de revisión KYC y del tiempo hasta la activación.

## ADDED Requirements

### Requirement: Registro de eventos de onboarding
El sistema SHALL registrar en el backend, con marca de tiempo del servidor y un identificador de corrida, los eventos de inicio del onboarding, finalización de cada paso, envío correcto de los datos, resultado del KYC, activación de la wallet y cierre del flujo. Los eventos SHALL persistirse en el backend y conservarse tras un reinicio del servicio.

#### Scenario: Inicio del flujo
- **WHEN** el usuario comienza el onboarding
- **THEN** se registra un evento de inicio con su marca de tiempo y una nueva corrida

#### Scenario: Paso completado
- **WHEN** el usuario completa un paso con datos válidos
- **THEN** se registra un evento con el nombre del paso y su marca de tiempo

#### Scenario: Cierre del flujo
- **WHEN** el flujo llega a un resultado final
- **THEN** se registra un evento de cierre con el resultado: `APPROVED`, `REJECTED` o abandonado

#### Scenario: Persistencia ante recarga
- **WHEN** el usuario recarga la página durante una corrida
- **THEN** los eventos ya registrados se conservan y la marca de inicio no se pierde

#### Scenario: Persistencia ante reinicio del backend
- **WHEN** el backend se reinicia
- **THEN** las corridas y eventos registrados siguen disponibles

#### Scenario: Marca de tiempo del servidor
- **WHEN** el backend registra un evento
- **THEN** la marca de tiempo es la hora del servidor y no un valor enviado por el cliente

#### Scenario: Abandono de una corrida anterior
- **WHEN** el usuario inicia una nueva corrida o usa la acción de reiniciar teniendo una corrida anterior sin cerrar
- **THEN** la corrida anterior se cierra con el resultado abandonado

### Requirement: Privacidad de los eventos
Los eventos y los datos que el backend persiste MUST NOT contener celular, nombre, fecha de nacimiento ni número de documento.

#### Scenario: Contenido de un evento
- **WHEN** se registra cualquier evento
- **THEN** contiene solo el identificador de corrida, el tipo de evento, la marca de tiempo y metadatos no personales, como el nombre del paso o el resultado

#### Scenario: Eventos exportados
- **WHEN** se exportan los eventos
- **THEN** el contenido no incluye celular, nombre, fecha de nacimiento ni número de documento

### Requirement: Duración del flujo del usuario
El sistema SHALL calcular la duración del flujo como el tiempo desde el evento de inicio hasta el primer envío de datos aceptado por el mock KYC. El instante del envío es el momento en que se envía la petición que el mock acepta con respuesta 201. Los reintentos por error de servicio ocurridos antes de ese envío SHALL contar dentro de esta duración.

#### Scenario: Flujo sin reintentos
- **WHEN** el usuario completa los 4 pasos y el mock acepta el primer envío
- **THEN** la duración del flujo es la diferencia entre la aceptación del envío y el inicio

#### Scenario: Flujo con KYC no disponible
- **WHEN** el primer envío recibe 503 y un reintento posterior recibe 201
- **THEN** la duración del flujo llega hasta el envío del reintento que recibió el 201 y no hasta el primer intento

#### Scenario: Flujo no completado
- **WHEN** la corrida se cierra sin envío aceptado
- **THEN** no tiene duración de flujo y se excluye de la mediana

### Requirement: Tiempos de revisión KYC y activación separados
El sistema SHALL reportar por separado, y MUST NOT incluir en la duración del flujo, el tiempo de revisión KYC, medido desde el envío aceptado hasta la decisión final del KYC, y el tiempo hasta la activación, medido desde el envío aceptado hasta que la wallet queda activa.

#### Scenario: Aprobación inmediata
- **WHEN** el mock responde `ACTIVE` en la creación
- **THEN** se reportan tiempo de revisión KYC y tiempo hasta la activación como el tiempo de esa respuesta, separados de la duración del flujo

#### Scenario: Revisión manual resuelta
- **WHEN** una verificación `PENDING` se resuelve con `ACTIVE`
- **THEN** el tiempo de revisión KYC y el tiempo hasta la activación llegan hasta esa resolución y no alteran la duración del flujo

#### Scenario: Rechazo
- **WHEN** la verificación termina en `REJECTED`
- **THEN** se reporta el tiempo de revisión KYC y el tiempo hasta la activación queda sin valor

#### Scenario: Revisión pendiente
- **WHEN** una verificación sigue en `PENDING`
- **THEN** los tiempos de revisión KYC y activación se muestran como pendientes

### Requirement: Mediana y comparación con la referencia
El sistema SHALL calcular la mediana de la duración del flujo sobre las corridas completadas registradas en el backend, de todos los usuarios de la demo, y mostrarla junto a la referencia de 3 minutos.

#### Scenario: Varias corridas completadas
- **WHEN** hay varias corridas con duración de flujo
- **THEN** el sistema muestra la mediana de esas duraciones, el número de corridas incluidas y la referencia de 3 minutos

#### Scenario: Sin corridas completadas
- **WHEN** no hay corridas con duración de flujo
- **THEN** el sistema indica que aún no hay datos para calcular la mediana

### Requirement: Salida del resumen de onboarding
Con el modo demo activo, el sistema SHALL ofrecer un resumen que muestre para cada corrida su duración de flujo, tiempo de revisión KYC, tiempo hasta la activación y resultado, y que permita exportar los eventos como JSON. Con el modo demo inactivo, el backend MUST rechazar con 403 las consultas de resumen y de eventos.

#### Scenario: Resumen por corrida
- **WHEN** el usuario abre el resumen en modo demo
- **THEN** ve para cada corrida la duración del flujo, el tiempo de revisión KYC, el tiempo hasta la activación y el resultado en columnas separadas

#### Scenario: Exportación
- **WHEN** el usuario exporta desde el resumen
- **THEN** descarga un JSON con los eventos, sin datos personales

#### Scenario: Consulta fuera del modo demo
- **WHEN** el modo demo no está activo y se consultan el resumen o los eventos
- **THEN** el backend responde 403 y no devuelve datos

### Requirement: Alcance de la medición
El sistema MUST NOT medir ni reportar checkout, usuarios activos mensuales ni costo de adquisición de clientes.

#### Scenario: Métricas fuera de alcance
- **WHEN** se muestra el resumen
- **THEN** no incluye métricas de checkout, usuarios activos mensuales ni costo de adquisición
