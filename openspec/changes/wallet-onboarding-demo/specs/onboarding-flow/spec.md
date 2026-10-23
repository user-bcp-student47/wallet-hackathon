# Spec Delta

## Purpose

Define el flujo guiado de onboarding de la wallet: pasos, indicador de avance, validación de datos y verificación simulada del celular, hasta dejar listos los datos para el KYC.

## ADDED Requirements

### Requirement: Secuencia de pasos visible
El flujo SHALL mostrar en todo momento la secuencia completa de pasos requeridos y el paso actual. La secuencia SHALL ser: celular, OTP, datos personales y documento.

#### Scenario: Paso actual identificado
- **WHEN** el usuario está en cualquier paso del onboarding
- **THEN** la pantalla muestra los 4 pasos en orden e identifica el paso actual y los ya completados

#### Scenario: Avance al completar un paso
- **WHEN** el usuario completa un paso con datos válidos
- **THEN** el flujo pasa al paso siguiente y el indicador se actualiza

### Requirement: Bloqueo de avance por datos inválidos
El flujo SHALL impedir avanzar cuando faltan datos obligatorios o hay valores inválidos, y SHALL mostrar un mensaje que indique cómo corregir cada campo.

#### Scenario: Campo obligatorio vacío
- **WHEN** el usuario intenta avanzar con un campo obligatorio vacío
- **THEN** el flujo no avanza y muestra un mensaje asociado al campo que indica qué debe completar

#### Scenario: Valor con formato inválido
- **WHEN** el usuario ingresa un valor con formato inválido, como un celular con dígitos incorrectos o un documento sin el prefijo `TEST-`
- **THEN** el flujo no avanza y el mensaje indica el formato esperado

#### Scenario: Corrección habilita el avance
- **WHEN** el usuario corrige el valor y vuelve a intentar avanzar
- **THEN** el mensaje de error desaparece y el flujo avanza

### Requirement: Celular de prueba
El flujo SHALL registrar un número de celular de prueba, MUST NOT enviar SMS reales y MUST NOT enviar el número al backend ni al mock.

#### Scenario: Celular válido
- **WHEN** el usuario ingresa un celular de 9 dígitos que empieza por 9 y avanza
- **THEN** el flujo pasa al paso de OTP

#### Scenario: Advertencia de datos de prueba
- **WHEN** se muestra el paso de celular
- **THEN** la pantalla indica que solo deben usarse datos de prueba

### Requirement: Verificación OTP simulada
El flujo SHALL verificar la titularidad del celular con un OTP simulado, validado en el frontend contra un código fijo de prueba, sin integración con ningún proveedor de mensajería y sin consultar al backend.

#### Scenario: OTP correcto
- **WHEN** el usuario ingresa el código OTP de prueba esperado
- **THEN** el paso se marca como completado y el flujo avanza a datos personales

#### Scenario: OTP incorrecto
- **WHEN** el usuario ingresa un código distinto al esperado
- **THEN** el flujo no avanza y muestra un mensaje que indica que el código es incorrecto y permite reintentar

#### Scenario: Código de prueba disponible
- **WHEN** se muestra el paso de OTP
- **THEN** la pantalla informa cuál es el código de prueba o cómo obtenerlo, ya que no se envía ningún SMS

### Requirement: Datos personales
El flujo SHALL solicitar nombre completo y fecha de nacimiento, y SHALL exigir que la persona tenga al menos 18 años. Estos datos MUST NOT enviarse al backend ni al mock KYC, y solo se conservan en memoria durante la corrida.

#### Scenario: Mayor de edad
- **WHEN** el usuario ingresa una fecha de nacimiento que corresponde a 18 años o más
- **THEN** el paso se completa y el flujo avanza al documento

#### Scenario: Menor de edad
- **WHEN** el usuario ingresa una fecha de nacimiento que corresponde a menos de 18 años
- **THEN** el flujo no avanza y muestra un mensaje que indica que se requiere ser mayor de edad

#### Scenario: Fecha futura o inválida
- **WHEN** el usuario ingresa una fecha futura o inexistente
- **THEN** el flujo no avanza y muestra un mensaje que indica el formato esperado

### Requirement: Documento de prueba
El flujo SHALL solicitar un documento ficticio con prefijo `TEST-` y SHALL usar siempre el tipo de documento `TEST`. MUST NOT aceptar documentos reales.

#### Scenario: Documento válido
- **WHEN** el usuario ingresa un documento que empieza por `TEST-`
- **THEN** el flujo permite enviar los datos al KYC

#### Scenario: Documento sin prefijo
- **WHEN** el usuario ingresa un documento que no empieza por `TEST-`
- **THEN** el flujo no permite enviar y muestra un mensaje que pide usar un documento de prueba con prefijo `TEST-`

### Requirement: Continuidad tras recarga
Si el usuario recarga la página durante una corrida, el flujo SHALL retomarse en el primer paso no completado, sin perder el tiempo de inicio de la corrida.

#### Scenario: Recarga a mitad del flujo
- **WHEN** el usuario recarga la página después de completar el paso de OTP
- **THEN** el flujo vuelve a mostrar el paso de datos personales, con los pasos ya completados marcados

#### Scenario: Recarga con la corrida cerrada
- **WHEN** el usuario recarga la página y la corrida ya tiene un resultado final
- **THEN** el flujo muestra el resultado y no vuelve a pedir los pasos

### Requirement: Vista responsive para smartphone
El flujo SHALL ser utilizable en pantallas de smartphone, sin desplazamiento horizontal.

#### Scenario: Pantalla de smartphone
- **WHEN** el flujo se muestra en un viewport de 360 px de ancho
- **THEN** todos los pasos y sus controles son visibles y operables sin desplazamiento horizontal
