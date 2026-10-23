# Wallet Onboarding Demo

Demo de onboarding de wallet para personas de 18 a 21 años en Perú. Valida un flujo guiado de 4 pasos (celular, OTP simulado, datos personales y documento) contra el mock KYC, y mide por separado la duración del flujo, la revisión KYC y la activación.

- **Alcance:** [alcance_onboarding.md](docs/alcance/alcance_onboarding.md)
- **Contrato del mock:** [openapi.json](docs/openapi.json)
- **Cambio OpenSpec:** `openspec/changes/wallet-onboarding-demo`

```
 Angular (frontend/, ng serve)  --/api-->  Quarkus (backend/, mvn quarkus:dev)  -->  mock KYC
```

Solo se usan datos de prueba (`TEST`). No se envían SMS reales ni se activan operaciones financieras.

## Requisitos

- JDK 21 y Maven 3.9+
- Node.js y Angular CLI (`npm i -g @angular/cli`)

## Ejecución

En dos terminales:

```bash
# 1. Backend (http://localhost:8080)
cd backend
mvn quarkus:dev

# 2. Frontend (http://localhost:4200); /api se redirige al backend con proxy.conf.json
cd frontend
npm install
ng serve
```

Abre http://localhost:4200.

## Modo demo

El modo demo se activa en el backend con la propiedad `onboarding.demo.enabled`. Con `mvn quarkus:dev` está activo por defecto; en otros perfiles está apagado.

Con el modo demo activo:
- El formulario muestra un selector de escenario KYC (`APPROVED`, `MANUAL_REVIEW`, `REJECTED`, `UNAVAILABLE`).
- En `MANUAL_REVIEW` aparecen las acciones para resolver con `ACTIVE` o `REJECTED`.
- El resumen muestra la duración del flujo, la revisión KYC, la activación, la mediana frente a la referencia de 3 minutos y permite exportar los eventos en JSON.

Con el modo demo apagado, el backend usa el escenario `APPROVED` y responde 403 a la resolución manual, al resumen y a los eventos.

Para forzarlo por variable de entorno: `ONBOARDING_DEMO_ENABLED=true` o `false`.

## OTP simulado

El OTP se valida en el frontend contra el código fijo `123456`, sin límite de intentos. No pasa por el backend.

## Cambiar de grupo del mock

El backend usa por defecto el grupo 5. Para cambiarlo:

```bash
# PowerShell
$env:ONBOARDING_MOCK_BASE_URL = "https://bcp-wallet-mock.yefri.workers.dev/groups/3"
mvn quarkus:dev
```

También se puede editar `onboarding.mock.base-url` en `backend/src/main/resources/application.properties`.

## Reiniciar los datos de la demo

Las corridas y eventos se guardan en H2 en `backend/data/`. No hay endpoint de limpieza: para empezar de cero, detén el backend y borra la carpeta `backend/data/`.

## Pruebas

```bash
cd backend && mvn verify
cd frontend && ng test --watch=false
```
