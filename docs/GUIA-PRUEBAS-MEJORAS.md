# Guía de pruebas manuales · Mejoras de noviembre

Esta guía sirve para probar a mano, en local, las mejoras de los PR #278 a #282: el portal en el navegador y la app en un
celular Android. Cada prueba indica qué hacer y qué se espera. Si algo no se comporta así, anótelo con el número de la
prueba (por ejemplo «B-07») y una captura.

| PR | Mejora | Feature | Paquete |
|---|---|---|---|
| #278 | Preferencias de avisos | FE-MOV-04 | `specs/HOTFIX-preferencias-de-avisos` |
| #280 | Gestión de socios | FE-WEB-05 | `specs/HOTFIX-gestion-de-socios` |
| #280 | Auditoría de suscripción | FE-WEB-08 | `specs/HOTFIX-auditoria-de-suscripcion` |
| #280 | Tablero operativo | FE-WEB-07 | `specs/HOTFIX-tablero-operativo` |
| #281 | Consumidor del outbox (avisos push por la cola) | ASR-05 | `specs/HOTFIX-consumidor-del-outbox` |
| #282 | Registro con documento y selfie | FE-MOV-01 | `specs/HOTFIX-onboarding-con-documento-y-selfie` |
| #282 | Escaneo de documentos | FE-MOV-05 | `specs/HOTFIX-escaneo-de-documentos` |
| #282 | Prestadores y asistencia | FE-MOV-07 | `specs/HOTFIX-prestadores-y-asistencia` |

El PR #279 (secretos en ECS) es de infraestructura y se prueba al desplegar; ver
`specs/HOTFIX-secretos-en-ecs` y `docs/ci-cd.md`.

Tiempo estimado: unos 40 minutos el portal y unos 60 minutos la app.

---

## 1. Preparar el entorno

Todo se prueba desde la rama `hotfix/app-onboarding-escaneo-prestadores` (PR #282), que incluye las ocho mejoras. Cuando
los PR estén en `dev`, use `dev`.

### 1.1 Servicios locales

```bash
git fetch origin && git switch hotfix/app-onboarding-escaneo-prestadores
docker compose up -d postgres floci     # PostgreSQL 16 y el emulador de S3/SQS
```

### 1.2 Backend

1. Copie `backend/.env.example` como `backend/.env` y llene `JWT_SECRET_KEY` y `OTP_SECRET_KEY` (el archivo explica cómo
   generarlas).
2. Agregue además, en `backend/.env`:
   ```
   ENVIRONMENT=development
   ORACLE_SECRET=<genere otra con: python -c "import secrets; print(secrets.token_urlsafe(48))">
   ```
   `development` hace que el backend siembre los usuarios de prueba y muestre en pantalla los códigos de verificación
   (no se envía SMS). `ORACLE_SECRET` solo hace falta para la prueba del sismo (M-35 a M-37).
3. Opcional, para recibir push reales en el celular (pruebas M-16 y M-36): ponga en `backend/.env`
   `FIREBASE_CREDENTIALS_FILE=<ruta a la llave de la cuenta de servicio de Firebase>` y deje el archivo
   `google-services.json` en `movil/app/`. Ninguno de los dos se sube al repositorio. Sin ellos, los avisos llegan igual
   cuando la app sincroniza, pero no como push.
4. Arranque:
   ```bash
   cd backend && poetry install && poetry run uvicorn app.main:app --port 8000
   ```
   Al arrancar, el backend aplica las migraciones (hasta la `0009`) y siembra usuarios, socios y prestadores de prueba.
   Compruebe `http://localhost:8000/health` → `{"status":"healthy",…}`.

### 1.3 Portal

```bash
cd web && npm ci && npx ng serve --proxy-config proxy.conf.json
```

Abra `http://localhost:4200`. (Si su backend no está en el puerto 8000, copie `proxy.conf.json` y cambie el puerto.)

### 1.4 App en el celular

Con el backend corriendo y `cloudflared` instalado:

```bash
./scripts/apk-local.sh            # PORT=8001 ./scripts/apk-local.sh si el backend usa otro puerto
```

El script abre un túnel temporal, compila un APK de debug que apunta a él y le dice dónde quedó. Instálelo en el celular
(Android 13 o superior). Si el túnel se cierra, vuelva a correr el script e instale el APK nuevo.

### 1.5 Usuarios de prueba

Todos son ficticios y solo existen en local.

| Usuario | Para qué | Contraseña |
|---|---|---|
| `asesor@solventa.com` | Portal · asesor (cotiza y emite) | `DEFAULT_SEED_PASSWORD` en `backend/app/modules/auth/service.py` |
| `operaciones@solventa.example` | Portal · analista **de Solventa** | La misma |
| `analista@bancoaliado.example` | Portal · analista **de un socio** (Banco Aliado) | La misma |
| `maria.ruiz@correo.co` (documento 1020304050) | App · asegurada | La misma |
| `pedro.gomez@correo.co` (documento 80123456) | App · asegurado | La misma |

El segundo factor del portal es un código TOTP: agregue `DEFAULT_SEED_TOTP_SECRET` (mismo archivo) a una app de
autenticación (Google Authenticator, Authy…). En la app del celular, el código de verificación aparece en la misma
pantalla mientras `ENVIRONMENT=development`.

### 1.6 Datos que debe crear antes

Varias pruebas necesitan que María tenga una póliza vigente en Bogotá. Si no tiene:

1. Entre al portal como **asesor**.
2. Cotice para documento `1020304050`, ciudad **Bogotá**, registre el consentimiento con el código que muestra la pantalla
   y emita la póliza con el código de firma. Pague con el medio de prueba que aprueba.
3. Anote el número de póliza (`POL-…`) y el de la cotización (`QUO-…`).

Para las pruebas de siniestros, reporte uno desde la app (M-20) antes de empezar esa sección.

---

## 2. Portal (navegador)

### Parte A · Pestañas y permisos

| # | Pasos | Resultado esperado |
|---|---|---|
| A-01 | Entre como `analista@bancoaliado.example`. | Solo ve «Pólizas» y «Siniestros». |
| A-02 | Escriba en la barra `localhost:4200/socios`, luego `/auditoria` y `/operacion`. | Las tres lo devuelven a «Pólizas». |
| A-03 | Cierre sesión y entre como `operaciones@solventa.example`. | Ve «Pólizas», «Siniestros», «Socios», «Auditoría» y «Operación». |
| A-04 | Abra las herramientas del navegador → Red, recargue la página. | El archivo inicial principal pesa unos 360 kB; cada pestaña descarga su propio archivo al abrirla. |

### Parte B · Socios (FE-WEB-05)

| # | Pasos | Resultado esperado |
|---|---|---|
| B-01 | Abra «Socios». | Tabla con los socios sembrados, correo enmascarado (`b***@…`), entorno y estado. |
| B-02 | Dé de alta «Seguros Bolívar Compañía» con un correo `@example.com`. | Aparece un panel con `client_id` y `client_secret`, el aviso de que no se volverán a mostrar y «Copiar». |
| B-03 | Pulse «Copiar» y luego «Ya las guardé». | Se copia el secreto; el panel se cierra y el foco vuelve a un elemento visible. |
| B-04 | Recargue la página. | El secreto ya no aparece en ninguna parte. El socio nuevo está en **sandbox** y su identificador no tiene tildes (`segurosbolivarcompan_…`). |
| B-05 | «Rotar credenciales» del socio nuevo y confirme. | Pide confirmación y muestra un secreto nuevo una sola vez, con el aviso de que los tokens ya emitidos siguen vigentes hasta vencer. |
| B-06 | «Aprobar a producción» y confirme. | Pasa a producción. |
| B-07 | «Revocar» y confirme. | Queda revocado; ya no ofrece rotar ni aprobar. |
| B-08 | Solo teclado: recorra la tabla con Tab, abra una confirmación con Enter y ciérrela con Esc. | Se puede usar todo sin mouse; al cerrar, el foco vuelve al botón que abrió la acción. |

### Parte C · Auditoría de suscripción (FE-WEB-08)

Use el `QUO-…` de la sección 1.6.

| # | Pasos | Resultado esperado |
|---|---|---|
| C-01 | Abra «Auditoría» y busque el `QUO-…` (pruebe también con un espacio delante). | Muestra entradas con el documento enmascarado, versión de reglas, decisión y desglose por plan. |
| C-02 | Pulse «Recalcular». | Dice «Coincide» y se anuncia (con lector de pantalla). |
| C-03 | Busque un identificador inventado, por ejemplo `QUO-000000000000`. | «No encontramos la decisión», sin errores técnicos. |
| C-04 | Con el asesor, cotice para un documento con suma asegurada muy alta hasta obtener un rechazo; busque el `UWR-…` en «Auditoría». | Muestra regla, motivo y montos, y explica que los rechazos no se recalculan (no guardan el puntaje del buró). No hay botón «Recalcular». |
| C-05 | «Registrar revocación del consentimiento» con el documento de María y confirme. | Queda registrada. |
| C-06 | Vuelva a buscar el `QUO-…`. | Una sola nota: «Se omiten los datos personales y los montos derivados de ellos». Solo se ven los planes con los nombres de sus coberturas: sin primas, sin montos, sin edad y sin «Recalcular». |
| C-07 | Con el asesor, intente cotizar otra vez para María con el consentimiento anterior. | Lo rechaza: «El consentimiento no es válido para este cliente» o pide uno nuevo. |
| C-08 | Registre un consentimiento nuevo para María. | Puede volver a cotizar. |

> Después de C-05 María queda sin consentimiento vigente. Registre uno nuevo (C-08) antes de seguir con la app.

### Parte D · Tablero operativo (FE-WEB-07)

| # | Pasos | Resultado esperado |
|---|---|---|
| D-01 | Abra «Operación». | Tarjetas de cotizaciones, cotizaciones por minuto, conversión, pólizas emitidas, siniestros en curso y pendientes del outbox. |
| D-02 | Cambie el periodo a «7 días» y «30 días». | Las cifras cambian según el periodo. |
| D-03 | Revise la tabla de latencia. | Aparece al menos la cotización de 1.6 con su p50/p95/p99 frente a la meta (400 ms para cotizar). |
| D-04 | Revise el semáforo de dependencias. | Base de datos, S3 y SQS «Operativa»; Firebase «Operativa» o «Sin configurar». Cada estado tiene texto e icono, no solo color. |
| D-05 | Deje la pestaña abierta 30 s. | Se actualiza sola; si cambia a otra pestaña del navegador, deja de actualizar hasta que vuelva. |
| D-06 | Detenga el backend y espere hasta un minuto. | Marca «Sin datos desde HH:mm:ss» en tarjetas y semáforo; nunca deja «Operativa» como si fuera actual. |
| D-07 | Vuelva a levantar el backend. | El tablero se recupera y avisa que los indicadores se actualizaron. |

### Parte E · Siniestros con documentos (FE-MOV-05, lado analista)

Hágala después de M-23 (documentos enviados desde la app).

| # | Pasos | Resultado esperado |
|---|---|---|
| E-01 | Como `operaciones@solventa.example`, abra «Siniestros» y el siniestro de María. | Sección «Documentos del asegurado» con un enlace por página: «Envío del dd/MM/yyyy HH:mm, página N». |
| E-02 | Abra un enlace. | Abre la imagen en otra pestaña (el enlace avisa que abre otra pestaña). La imagen no conserva datos EXIF. |
| E-03 | Revise el historial del siniestro. | Aparece «Documentos agregados». |
| E-04 | Como `analista@bancoaliado.example`, intente abrir ese siniestro. | No lo ve (no es de su socio). |

---

## 3. App (celular)

### Parte A · Registro con documento y selfie (FE-MOV-01)

Use correos `@example.com` y documentos inventados. El verificador simulado decide así: documento terminado en **9** →
rechazado; terminado en **8** → en verificación; cualquier otro → aprobado.

| # | Pasos | Resultado esperado |
|---|---|---|
| M-01 | En la pantalla de ingreso, pulse «Crear cuenta». | Abre el registro. |
| M-02 | Llene los datos con una clave débil (`12345678`). | No deja seguir; indica los requisitos de la clave. |
| M-03 | Corrija la clave, use el documento `52000011` y pida el código. Escríbalo. | Acepta el código (en pantalla aparece el de prueba). |
| M-04 | Tome la foto del documento con la cámara trasera. | Muestra la vista previa de la cámara trasera. |
| M-05 | Siga a la selfie. | **Cambia a la cámara frontal.** |
| M-06 | Acepte la autorización biométrica y envíe. | «Cuenta creada», ofrece activar la huella y entra a la billetera. |
| M-07 | Repita con el documento `52000018`. | «Estamos verificando su identidad». Al entrar, la billetera muestra ese mismo aviso (no «Sin conexión»). |
| M-08 | Repita con el documento `52000019`. | «No pudimos verificar su identidad». No crea la cuenta. |
| M-09 | Intente registrarse otra vez con `52000011` o con el mismo correo. | «No pudimos crear la cuenta con esos datos», sin decir cuál dato está repetido. |
| M-10 | Pida el código cuatro veces seguidas para el mismo teléfono. | A partir del cuarto lo rechaza por límite. |
| M-11 | Con la cuenta de M-07, vaya a «Avisos» → «Revocar autorización» y confirme. | Revoca, cierra la sesión de esa cuenta y el documento `52000018` queda libre para registrarse de nuevo. |
| M-12 | Con TalkBack, recorra el registro. | Lee cada campo, el paso actual, el obturador y los avisos. |

### Parte B · Preferencias de avisos (FE-MOV-04) y avisos por la cola (ASR-05)

Entre como María. Las pruebas de push necesitan Firebase configurado (sección 1.2, paso 3).

| # | Pasos | Resultado esperado |
|---|---|---|
| M-13 | En la billetera, pulse «Avisos». | Tres interruptores encendidos: Siniestros, Vencimientos y Pagos, cada uno con su explicación. |
| M-14 | Apague «Siniestros». | Dice «Guardado». |
| M-15 | En el portal, como `operaciones@solventa.example`, cambie el estado de un siniestro de María (asignar, aprobar o rechazar). | **No** llega push ni aviso en el celular; el cambio sí se ve al abrir «Mis siniestros». |
| M-16 | Encienda «Siniestros» y cambie el estado de otro siniestro. | Llega el push «Solventa · Su siniestro» en unos segundos (lo envía el consumidor de la cola, no la petición del analista). |
| M-17 | Active el modo avión, abra «Avisos» y apague «Pagos». | Muestra la copia del teléfono y avisa que se guardará al volver la red. |
| M-18 | Quite el modo avión, espere a que sincronice y vuelva a abrir «Avisos». | «Pagos» sigue apagado (se guardó en el servidor). |
| M-19 | Cierre sesión y vuelva a entrar. | Las preferencias siguen como las dejó (vienen del servidor). |

### Parte C · Siniestros y escaneo de documentos (FE-MOV-05)

| # | Pasos | Resultado esperado |
|---|---|---|
| M-20 | «Reportar un siniestro» de la póliza de 1.6, con fotos, y envíelo. | Constancia con radicado `SIN-…`; aparece en «Mis siniestros». |
| M-21 | Abra el siniestro en «Mis siniestros». | Ofrece «Agregar documentos» (solo si está recibido o en evaluación). |
| M-22 | «Agregar documentos» → «Escanear documento». Escanee dos páginas. | Abre el escáner de Google (recorta y endereza). Muestra las páginas con su miniatura. |
| M-23 | Quite una página y agregue otra; luego «Enviar». | «Recibimos N páginas». En el portal se ven (prueba E-01). |
| M-24 | Repita el envío con modo avión. | «Sin conexión, intente de nuevo»; las páginas siguen en pantalla. Al volver la red y reintentar, se envían una sola vez. |
| M-25 | En un siniestro ya decidido, busque «Agregar documentos». | No aparece. |
| M-26 | Si tiene un teléfono sin Google Play services, repita M-22. | Usa la cámara para fotografiar cada página. |
| M-27 | Con TalkBack, recorra la pantalla de documentos. | Lee el número de cada página y «Quitar la página N». |

### Parte D · Prestadores y asistencia (FE-MOV-07)

Los prestadores de prueba están en Bogotá, Medellín, Cali, Barranquilla y Bucaramanga, y la búsqueda llega hasta 50 km.

| # | Pasos | Resultado esperado |
|---|---|---|
| M-28 | En la billetera, pulse «Asistencia» y luego «Elegir ciudad» → Bogotá. | Lista de prestadores de Bogotá con su servicio. |
| M-29 | Filtre por «Grúa». | Solo quedan los que ofrecen grúa. |
| M-30 | Pulse «Usar mi ubicación» y permita la ubicación aproximada. | Si está a menos de 50 km de una de esas ciudades, los ordena por distancia; si no, muestra que no hay prestadores para esa búsqueda. |
| M-31 | En un prestador, pulse «Llamar» y «Ver en el mapa». | Abre el marcador (sin llamar solo) y la app de mapas del teléfono. |
| M-32 | «Pedir asistencia» con la póliza de 1.6 y una descripción. | «Solicitud recibida», el prestador más cercano, «Tiempo estimado si lo contacta» y «Llámelo para coordinar». **No** dice «asignado» ni «en camino». |
| M-33 | Active el modo avión y abra «Asistencia». | Muestra la última lista guardada con su fecha. |
| M-34 | Pida asistencia cinco veces seguidas en menos de una hora. | La sexta la rechaza por límite y sugiere llamar a la línea de Solventa (en debug hay una línea ficticia). |

### Parte E · Siniestro paramétrico por sismo (HU-MOV-10)

Necesita `ORACLE_SECRET` en `backend/.env` (sección 1.2) y una póliza vigente de María en Bogotá.

| # | Pasos | Resultado esperado |
|---|---|---|
| M-35 | `python scripts/oraculo_sismo.py Bogotá 5.0` | Responde 200 sin siniestros (no alcanza el umbral de 6,5). |
| M-36 | `python scripts/oraculo_sismo.py Bogotá 7.1` | Responde 200 con los radicados creados. En la app, el siniestro aparece aprobado y luego pagado; con Firebase, llegan los push de aprobado y pagado. |
| M-37 | Repita M-36 cambiando la llave en el script (o sin `ORACLE_SECRET`). | 401 (firma inválida) o 503 (canal cerrado). |

---

## 4. Qué reportar

Para cada falla: número de la prueba, qué hizo, qué esperaba, qué pasó y una captura. En la app indique el modelo del
celular y la versión de Android.

Cuando termine, registre el resultado como evidencia de la prueba manual de cada paquete (`test-cases.md`):

| Paquete | Prueba manual |
|---|---|
| HOTFIX-preferencias-de-avisos | TC-07 |
| HOTFIX-gestion-de-socios | TC-07 |
| HOTFIX-auditoria-de-suscripcion | TC-11 |
| HOTFIX-tablero-operativo | TC-09 |
| HOTFIX-consumidor-del-outbox | TC-11 |
| HOTFIX-onboarding-con-documento-y-selfie | TC-17 |
| HOTFIX-escaneo-de-documentos | TC-16 |
| HOTFIX-prestadores-y-asistencia | TC-15 |

## 5. Limitaciones conocidas (no son fallas)

- Integraciones simuladas: la verificación de identidad, la pasarela de pagos, el buró de crédito y el oráculo de
  eventos. El SMS sale en pantalla en lugar de enviarse.
- El registro desde la app está apagado fuera de desarrollo y el botón «Crear cuenta» no aparece en el APK de release.
- «Asistencia» no aparece en el APK de release hasta que exista una línea de atención real, y no despacha al prestador:
  el asegurado lo contacta.
- En producción no hay catálogo de prestadores.
