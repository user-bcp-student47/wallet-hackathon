# Proposal

## Why

La referencia de producto es bajar el tiempo mediano de onboarding de la wallet de 8 a 3 minutos para personas de 18 a 21 años en Perú que no están bancarizadas. Antes de lanzar, se necesita validar con una demo un flujo guiado que lleve a completar el registro, muestre claramente el estado de la wallet y permita medir la duración del flujo sin mezclarla con la revisión KYC ni con la activación.

El repositorio solo tiene documentación y el contrato del mock (`docs/openapi.json`); no existe código de aplicación. El stack definido es Angular para el frontend y Quarkus para el backend.

## What Changes

- Nuevo frontend Angular, responsive para smartphone, con un onboarding de 4 pasos: celular de prueba, OTP, datos personales (nombre y fecha de nacimiento) y documento `TEST-`.
- El OTP se valida de forma simulada en el frontend, con un código fijo de prueba. No se envían SMS ni se usan números reales.
- Indicador de la secuencia de pasos y del paso actual. Validación que bloquea el avance con mensajes accionables.
- Nuevo backend Quarkus que actúa como única vía hacia el mock KYC (`POST /kyc/verifications`, `GET /kyc/verifications/{id}`, `POST /kyc/verifications/{id}/decision`). El frontend nunca llama al mock directamente.
- El backend registra los eventos de onboarding en una base H2 con marcas de tiempo del servidor y calcula la duración del flujo, el tiempo de revisión KYC, el tiempo hasta la activación y la mediana.
- Pantallas de resultado para `APPROVED`, `MANUAL_REVIEW` y `REJECTED`. El 503 `KYC_UNAVAILABLE` se trata como un error recuperable con reintento.
- Modo demo activado por configuración del backend: selector de escenario KYC, botones `ACTIVE` / `REJECTED` en la revisión manual y resumen con exportación JSON. No hay consola de soporte independiente.
- El celular, el nombre y la fecha de nacimiento no salen del navegador. Al backend solo llega el documento `TEST-`, que se reenvía al mock sin guardarlo.

## Capabilities

### New Capabilities
- `onboarding-flow`: pasos guiados, indicador de avance, validación de datos y OTP simulado en el frontend, hasta enviar los datos al KYC.
- `onboarding-kyc-outcome`: envío al mock KYC a través del backend, resultados `APPROVED` / `MANUAL_REVIEW` / `REJECTED`, error recuperable `UNAVAILABLE`, resolución manual de demo y selector de escenario.
- `onboarding-analytics`: eventos de inicio, paso y cierre persistidos en el backend, y cálculo y reporte de la duración del flujo, el tiempo de revisión KYC y el tiempo de activación.

### Modified Capabilities
<!-- Ninguna: openspec/specs/ está vacío. -->

## Impact

- Código nuevo: `frontend/` (Angular) y `backend/` (Quarkus con API REST, cliente REST del mock y persistencia H2).
- Dependencia externa: contrato del mock en [openapi.json](../../../docs/openapi.json), consumido solo por el backend. La URL base del grupo se configura en el backend. Solo se envían datos `TEST`.
- Fuera de alcance: pagos, QR, historial, idempotencia de pagos, fallas y reversos, soporte de pagos, conciliación y ledger, KYC real, autenticación de usuarios, y medición de MAU, CAC o checkout.
- El alcance de onboarding prevalece sobre la historia `user-onboarding-mvp-completion`, que además pide MAU/CAC y acceso al punto de entrada de pagos.
- Limitación: la demo no tiene autenticación; los endpoints de métricas y de decisión manual solo existen con el modo demo activado.
