# Spec Delta

## Purpose

Define cómo el onboarding envía los datos al KYC simulado y cómo comunica al usuario el resultado: wallet activa, revisión manual pendiente o rechazo, incluyendo los controles de demo para probar cada escenario.

## ADDED Requirements

### Requirement: Backend como única vía al mock
El frontend SHALL comunicarse con el mock KYC exclusivamente a través del backend. El backend SHALL enviar al mock únicamente `documentType` igual a `TEST`, `document` con prefijo `TEST-` y el `scenario` elegido, y MUST NOT enviar ni recibir celular, nombre ni fecha de nacimiento.

#### Scenario: Envío con datos válidos
- **WHEN** el usuario confirma el último paso con todos los datos válidos
- **THEN** el backend crea la verificación en el mock con `documentType` `TEST`, el documento ingresado y el escenario seleccionado

#### Scenario: Documento sin prefijo rechazado por el backend
- **WHEN** el backend recibe un documento que no empieza por `TEST-`
- **THEN** responde con error de validación y no llama al mock

#### Scenario: El documento no se conserva
- **WHEN** el backend procesa un envío
- **THEN** el número de documento no queda guardado en la base de datos ni en los registros del servidor

### Requirement: Un solo envío aceptado por corrida
El sistema SHALL permitir como máximo una verificación aceptada por el mock por corrida, y MUST impedir envíos simultáneos duplicados.

#### Scenario: Doble envío bloqueado en la interfaz
- **WHEN** hay un envío en curso y el usuario vuelve a pulsar el botón de enviar
- **THEN** el sistema no crea una segunda verificación

#### Scenario: Segundo envío tras una verificación aceptada
- **WHEN** el backend recibe un envío para una corrida que ya tiene una verificación aceptada
- **THEN** responde con conflicto y no llama al mock

### Requirement: Resultado aprobado
Cuando la verificación resulte aprobada, el sistema SHALL mostrar que el registro fue aprobado y que la wallet está activa.

#### Scenario: Wallet activa
- **WHEN** el mock responde con estado `ACTIVE`
- **THEN** la pantalla de resultado muestra `APPROVED`, indica que la wallet está activa y muestra el identificador de la wallet

#### Scenario: Sin operaciones financieras
- **WHEN** se muestra el resultado aprobado
- **THEN** no se ofrece ninguna operación financiera, como pagos o recargas

### Requirement: Resultado en revisión manual
Cuando la verificación quede pendiente, el sistema SHALL mostrar que el registro requiere revisión manual y que la wallet aún no está activa.

#### Scenario: Revisión pendiente
- **WHEN** el mock responde con estado `PENDING`
- **THEN** la pantalla de resultado muestra `MANUAL_REVIEW` y el estado pendiente, e indica que la wallet no está activa

#### Scenario: Resolución a activa
- **WHEN** en modo demo se resuelve la revisión con `ACTIVE`
- **THEN** el sistema consulta el estado actualizado y muestra la wallet como activa

#### Scenario: Resolución a rechazada
- **WHEN** en modo demo se resuelve la revisión con `REJECTED`
- **THEN** el sistema consulta el estado actualizado y muestra el registro como rechazado

#### Scenario: Decisión repetida
- **WHEN** se repite una decisión ya aplicada
- **THEN** el sistema muestra el mismo estado final sin error

#### Scenario: Conflicto de decisión
- **WHEN** el mock responde 409 `DECISION_CONFLICT` al intentar cambiar una decisión final
- **THEN** el sistema muestra un mensaje de conflicto y conserva el estado final ya registrado

### Requirement: Resultado rechazado
Cuando la verificación sea rechazada, el sistema SHALL mostrar el estado rechazado y MUST NOT presentar la wallet como activa.

#### Scenario: Registro rechazado
- **WHEN** el mock responde con estado `REJECTED`
- **THEN** la pantalla de resultado muestra `REJECTED` e indica que la wallet no está activa

### Requirement: KYC no disponible
Si el mock responde 503 `KYC_UNAVAILABLE`, el backend SHALL devolver el mismo error con código estable y el frontend SHALL tratarlo como un error recuperable: informar que el servicio no está disponible y permitir reintentar sin volver a ingresar los datos. No se crea wallet.

#### Scenario: Servicio no disponible
- **WHEN** el mock responde 503 `KYC_UNAVAILABLE` al enviar
- **THEN** el sistema muestra un mensaje de servicio no disponible, conserva los datos ingresados y ofrece reintentar

#### Scenario: Reintento exitoso
- **WHEN** el usuario reintenta y el mock responde 201
- **THEN** el sistema muestra el resultado correspondiente

#### Scenario: Error de red u otro error del mock
- **WHEN** la petición del backend al mock falla por red o el mock responde con otro error
- **THEN** el frontend muestra un mensaje accionable y permite reintentar sin crear estado inconsistente

### Requirement: Modo demo para escenarios y resolución manual
El modo demo SHALL activarse por configuración del backend. Con el modo demo activo, el sistema SHALL ofrecer un selector de escenario KYC (`APPROVED`, `MANUAL_REVIEW`, `REJECTED`, `UNAVAILABLE`) y, en la pantalla de revisión manual, acciones para resolver con `ACTIVE` o `REJECTED`. Con el modo demo inactivo, el backend MUST usar el escenario `APPROVED` y rechazar la resolución manual, y no habrá consola de soporte independiente.

#### Scenario: Selector visible en modo demo
- **WHEN** el modo demo está activo
- **THEN** el usuario puede elegir el escenario KYC antes de enviar, con `APPROVED` como valor por defecto

#### Scenario: Controles ocultos fuera del modo demo
- **WHEN** el modo demo no está activo
- **THEN** no se muestran el selector de escenario ni las acciones de resolución manual

#### Scenario: Backend rechaza acciones de demo fuera del modo demo
- **WHEN** el modo demo no está activo y el backend recibe un escenario distinto de `APPROVED` o una resolución manual
- **THEN** usa `APPROVED` para el escenario y responde 403 a la resolución manual, sin llamar al mock en este último caso

#### Scenario: Acciones de resolución en revisión pendiente
- **WHEN** el modo demo está activo y el resultado es `MANUAL_REVIEW`
- **THEN** la pantalla ofrece las acciones `ACTIVE` y `REJECTED`

### Requirement: Datos de prueba únicamente
El sistema MUST usar exclusivamente datos `TEST`, y SHALL indicar en la interfaz que se trata de una demo sin activación de operaciones financieras reales.

#### Scenario: Aviso de demo
- **WHEN** se muestra cualquier pantalla del flujo
- **THEN** la interfaz indica que es una demo con datos de prueba
