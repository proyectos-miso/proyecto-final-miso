# Pendientes de rutas críticas · HU-MOV-09

**Historia:** HU-MOV-09 · Enviar el reporte aunque no tenga señal ([#75](https://github.com/proyectos-miso/proyecto-final-miso/issues/75)) · **Responsable:** David · **Sprint 3**

**Por qué aquí:** el reporte sin señal (J3) solo sirve si el asegurado instala una app de release, y hoy no hay APK firmado para distribuirla.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-11 | J3 | Viene de HU-MOV-08, que calcula la huella SHA-256 de cada foto al capturarla y la guarda con ella | Verificar la huella al recibir cada foto en `POST /api/v1/insured/claims` o en la carga a S3 (`ChecksumSHA256` en la URL prefirmada) y rechazar la que no coincida | WHEN el backend recibe una foto, THE sistema SHALL verificar su hash y rechazar la que no coincida |
| RC-12 | J3 | No hay APK de release firmado ni `assembleRelease` en el CI | `signingConfig` con la keystore en los secretos de GitHub y un job de release en el CI que publique el APK como artefacto | WHEN se integra a `dev`, THE CI SHALL producir un APK de release firmado |
