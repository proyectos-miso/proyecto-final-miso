# Pendientes de rutas críticas · HU-MOV-09

**Historia:** HU-MOV-09 · Enviar el reporte aunque no tenga señal ([#75](https://github.com/proyectos-miso/proyecto-final-miso/issues/75)) · **Responsable:** David · **Sprint 3**

**Por qué aquí:** el reporte sin señal (J3) solo sirve si el asegurado instala una app de release, y hoy no hay APK firmado para distribuirla.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-12 | J3 | No hay APK de release firmado ni `assembleRelease` en el CI | `signingConfig` con la keystore en los secretos de GitHub y un job de release en el CI que publique el APK como artefacto | WHEN se integra a `dev`, THE CI SHALL producir un APK de release firmado |
