# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Ubicación aproximada del asegurado (latitud y longitud redondeadas a 3 decimales) | CONFIDENTIAL | YES | NO | Ordenar los prestadores por distancia y sugerir el más cercano |
| DATA-02 | Solicitud de asistencia: cuenta, póliza, servicio, descripción libre, prestador, distancia, ciudad del prestador, tiempo estimado y hora | RESTRICTED | YES | YES | Registrar la asistencia pedida y mostrarla al asegurado; pedir atención médica o la descripción pueden revelar datos de salud |
| DATA-03 | Copia de la última lista de prestadores en el teléfono, con distancias y la ciudad buscada | CONFIDENTIAL | YES | NO | Ver los prestadores sin conexión; las distancias permiten aproximar dónde estaba el asegurado |
| DATA-04 | Catálogo de prestadores (nombre, servicios, dirección, teléfono y coordenadas del negocio), ficticio en desarrollo | PUBLIC | NO | NO | Mostrar a quién llamar y dónde está |

## 2. Data lifecycle

### DATA-01 · Ubicación aproximada del asegurado

| Question | Answer |
|---|---|
| What it contains | Latitud y longitud con 3 decimales (unos 110 m) |
| Why it is needed | AC-01 y AC-06: ordenar y sugerir por distancia |
| Who can access it | La app mientras la pantalla está abierta; el backend durante la petición |
| Where it is captured | `AndroidApproximateLocator`, una sola lectura con el permiso de ubicación aproximada, al abrir «Asistencia» o tocar «Usar mi ubicación» |
| How it is transmitted | HTTPS: cuerpo de `POST /insured/providers/search` y de `POST /insured/assistance`; nunca en la URL |
| Where it is stored | En ningún lado: el servidor no la guarda (AC-09) y la app la tiene en memoria del ViewModel |
| Copies, caches and backups | Ninguna en el servidor: no va en la URL (los registros de acceso no la ven) y la huella SHA-256 de `idempotency_records` se calcula sin `lat` ni `lng`; la distancia al prestador queda en la solicitud y en la copia del teléfono |
| How long it is kept | En la app hasta salir de la pantalla o cerrar sesión; en el servidor, la duración de la petición |
| How access is audited | No se registra |
| How it is deleted or anonymized | Se descarta al terminar la petición; en la app se suelta al cerrar la pantalla o la sesión |
| What happens if it leaks | Revela dónde estaba el asegurado con unos 110 m de error: impacto medio |
| Transfers to third parties | Ninguna: no hay SDK de mapas; la app de mapas recibe solo la ubicación del prestador |

### DATA-02 · Solicitud de asistencia

| Question | Answer |
|---|---|
| What it contains | `user_id`, `policy_number`, servicio, descripción de hasta 300 caracteres, prestador, distancia, ciudad del prestador, tiempo estimado, estado y hora |
| Why it is needed | AC-06, AC-14: registrar qué pidió y qué prestador se le sugirió, y mostrárselo al asegurado |
| Who can access it | El asegurado dueño (`GET /insured/assistance`) y el backend; ningún analista en este cambio |
| Where it is captured | Formulario «Pedir asistencia» de la app |
| How it is transmitted | HTTPS |
| Where it is stored | `assistance_requests`, en estado RECEIVED; la respuesta (sin descripción ni coordenadas) en `idempotency_records` hasta la purga diaria de los de más de 24 h; el outbox lleva solo `request_id`, `provider_id` y servicio |
| Copies, caches and backups | Respaldos de RDS; mensajes de SQS hasta que el consumidor los descarta; la app no guarda la solicitud |
| How long it is kept | Sin purga definida (RISK-03); se borra en cascada si se borra la cuenta |
| How access is audited | La fila de `assistance_requests` (cuenta, póliza, servicio, prestador sugerido y hora). El evento `AssistanceRequested` no sirve como auditoría: está en `IGNORED_EVENT_TYPES` y el consumidor lo borra sin que nadie lo lea |
| How it is deleted or anonymized | `ON DELETE CASCADE` con la cuenta del asegurado; sin purga por antigüedad en este cambio |
| What happens if it leaks | Revela que el asegurado pidió asistencia, de qué tipo (incluida atención médica) y lo que escribió: impacto alto |
| Transfers to third parties | Ninguna: no hay despacho al prestador en este cambio |

### DATA-03 · Copia de la lista en el teléfono

| Question | Answer |
|---|---|
| What it contains | Hasta 20 prestadores con su distancia, la ciudad buscada y la hora de la copia |
| Why it is needed | AC-05: ver la lista sin conexión |
| Who can access it | La app con la sesión de la cuenta |
| Where it is captured | Al recibir la lista del servidor |
| How it is transmitted | No sale del teléfono |
| Where it is stored | Tabla `providers_copy` de la base SQLCipher de la billetera de la cuenta, con su frase envuelta en el Keystore |
| Copies, caches and backups | Ninguna: respaldos y transferencias excluidos por `dataExtractionRules`; no va al estado guardado |
| How long it is kept | Hasta la siguiente búsqueda con red o el cierre de sesión |
| How access is audited | No aplica en el teléfono |
| How it is deleted or anonymized | Se reemplaza en cada búsqueda y se borra con la base de la billetera al cerrar sesión o al desvincular el teléfono |
| What happens if it leaks | Con las distancias se puede aproximar dónde estuvo el asegurado: impacto bajo, cifrada |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado aprobado | insured, APPROVED | Autenticado | Ver prestadores y ciudades, pedir asistencia con su póliza vigente y ver sus solicitudes |
| Asegurado en verificación | insured, PENDING | Autenticado | Nada en estas rutas (403) |
| Analista u otro rol | analyst | Interno | Nada en estas rutas (403) |
| Marcador y app de mapas del teléfono | Apps del sistema | Tercero en el teléfono | Recibir el teléfono o la ubicación del prestador al tocar «Llamar» o «Ver en el mapa» |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| App → backend | Teléfono e internet | Token de acceso, identidad aprobada, rangos de coordenadas, patrones de ciudad, póliza y llave, servicio de la lista, longitud de la descripción, cuerpo sin campos extra |
| Backend → base | Backend y RDS | Consultas parametrizadas filtradas por `user_id` o `insured_document` del token |
| App → otras apps | App y marcador o mapas | Solo datos del prestador; `ACTION_DIAL` no llama sin que el asegurado lo confirme |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-01 | HTTPS | CRY-01, CTL-04, CTL-06 |
| App | Backend | DATA-02 | HTTPS | CRY-01, CTL-01, CTL-02, CTL-06 |
| Backend | `assistance_requests`, `idempotency_records` y outbox | DATA-02 (sin coordenadas) | Base | CTL-02, CTL-04 |
| Backend | App | DATA-04 con distancias | HTTPS | CRY-01 |
| App | Base cifrada de la billetera | DATA-03 | Local | CRY-02, CTL-05 |

## 6. Third-party services

N/A: no hay servicios externos nuevos; el marcador y la app de mapas del teléfono reciben solo datos del prestador
(DATA-04), que son públicos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /insured/providers/search | Autenticada (insured) | Cuerpo `{lat, lng, city, service}` |
| GET /insured/catalogs/cities | Autenticada (insured) | — |
| POST /insured/assistance | Autenticada (insured) | `Idempotency-Key` y el cuerpo JSON |
| GET /insured/assistance | Autenticada (insured) | — |
| Pantallas de la app | Teléfono del asegurado | Lo que escribe en la descripción |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | Póliza y solicitudes | Pedir asistencia con una póliza ajena o vencida, ver solicitudes de otro o usar la ruta sin identidad aprobada | Asegurado malicioso | MEDIUM | MEDIUM | MEDIUM | CTL-01 |
| THR-02 | T | Solicitudes | Un reintento o doble toque crea varias solicitudes, o la misma llave se reutiliza con otro cuerpo | Error de red o asegurado | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | D | Prestadores | Una cuenta inunda de solicitudes | Asegurado malicioso | LOW | MEDIUM | LOW | CTL-03 |
| THR-04 | I | DATA-01, DATA-03 | Reconstruir la ubicación del asegurado desde la base, registros, eventos o el teléfono | Interno o ladrón | LOW | HIGH | MEDIUM | CTL-04, CTL-05 |
| THR-05 | D | Backend | Entradas enormes o malformadas | Persona maliciosa | LOW | LOW | LOW | CTL-06 |
| THR-06 | S | Asegurado | Un catálogo ficticio en producción, o una línea de Solventa sin número, hace que el asegurado llame a números que no existen en una emergencia | Error de despliegue | LOW | HIGH | MEDIUM | CTL-07 |
| THR-07 | S | Asegurado | Sin despacho real, el asegurado cree que la ayuda ya va en camino y no la busca por otro lado | Diseño del producto | MEDIUM | HIGH | HIGH | CTL-08 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `insured_account` (sesión de acceso con identidad APPROVED) en las cuatro rutas: el token de solo reporte da 401 y la identidad en verificación 403 con `code` `IDENTITY_IN_REVIEW`; la póliza se busca por número, `insured_document` del token, `ACTIVE` y `valid_until` futura; ajena, inexistente o vencida 404; el listado filtra por `user_id` del token | API | EXISTING | THR-01, DATA-02 | TC-03, TC-04, TC-08 | T-01, T-02 | API1:2023, CWE-639 | — |
| CTL-02 | `Idempotency-Key` obligatorio con patrón; `find_response` y `save_response` con el espacio `insured:{user_id}` en la misma transacción que la solicitud y el outbox, con la huella sin `lat` ni `lng`; otro cuerpo 409 con `code` `IDEMPOTENCY_KEY_REUSED`; la carrera la resuelve la llave primaria y devuelve la respuesta del ganador; la app genera la llave por formulario, la conserva al reintentar y la renueva al cambiar el formulario o ante `IDEMPOTENCY_KEY_REUSED` | BACKEND | EXISTING | THR-02 | TC-05, TC-11 | T-02, T-03 | CWE-841 | — |
| CTL-03 | Tope de 5 solicitudes por asegurado en 60 minutos, contadas después de bloquear la cuenta con `FOR UPDATE` para que las simultáneas vayan en serie; 429 con `code` `TOO_MANY_REQUESTS` y `Retry-After` en segundos hasta que la más antigua salga de la ventana | API | EXISTING | THR-03 | TC-06 | T-02 | API4:2023, CWE-770 | — |
| CTL-04 | El servidor no guarda coordenadas ni las recibe en la URL: la búsqueda es `POST /insured/providers/search` con la ubicación en el cuerpo; `assistance_requests` no tiene columnas de ubicación, la respuesta guardada no las incluye, la huella de idempotencia se calcula sin `lat` ni `lng` y la tarea diaria borra los registros de más de 24 h; el evento lleva solo `request_id`, `provider_id` y servicio; la búsqueda no escribe nada | BACKEND | EXISTING | THR-04, DATA-01 | TC-02, TC-09, TC-10 | T-01, T-02 | CWE-359, CWE-598 | — |
| CTL-05 | En la app: permiso de ubicación aproximada, una sola lectura sin escuchar, coordenadas redondeadas a 3 decimales y solo en memoria (sin registros, `rememberSaveable` ni preferencias); copia de la lista en la base SQLCipher de la billetera de la cuenta, borrada al cerrar sesión; `ACTION_DIAL` sin permiso de llamadas y `geo:` sin SDK ni llave de API | MOBILE | EXISTING | THR-04, DATA-03 | TC-10, TC-12 | T-03 | MASVS-STORAGE-2, MASVS-PRIVACY-1 | — |
| CTL-06 | Pydantic con `extra="forbid"` en `ProviderSearch` y en la solicitud: latitud de −90 a 90, longitud de −180 a 180, ambas o ninguna, ciudad y póliza con patrón, servicio de una lista y descripción de 300; llave de 8 a 128 letras, números o guiones; consultas parametrizadas, también el filtro por servicio en SQL | BACKEND | EXISTING | THR-05 | TC-07 | T-01, T-02 | CWE-20 | — |
| CTL-07 | El catálogo ficticio solo se siembra con `settings.debug_enabled` (fuera de producción) y en su propio `try`; la migración no siembra; la línea ficticia de Solventa solo existe en los recursos de depuración; vacía (release), oculta «Asistencia» en la billetera y los avisos usan textos que no mandan a llamar a la línea; los mensajes del backend tampoco la mencionan | BACKEND | EXISTING | THR-06 | TC-13, TC-16 | T-01, T-03 | — | — |
| CTL-08 | La solicitud queda RECEIVED con el prestador como sugerencia; la app dice «Solicitud recibida», «El prestador más cercano es …», «Tiempo estimado si lo contacta» y «Llámelo para coordinar la ayuda», con «Llamar a …» como acción principal; ningún texto dice asignado ni en camino | MOBILE | EXISTING | THR-07 | TC-08, TC-14 | T-02, T-03 | — | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior); la app exige HTTPS fuera de desarrollo |
| Rationale | Viajan por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión y la app muestra la copia o el aviso sin conexión |
| Tests | TC-10 |

### CRY-02 · Copia de la lista en el teléfono

| Field | Value |
|---|---|
| Protects | DATA-03 |
| Type | AT_REST |
| Algorithm or service | SQLCipher de la base de la billetera, con la frase envuelta por una llave AES-GCM del Android Keystore (sin cambio, HU-MOV-04) |
| Rationale | La copia permite aproximar la ubicación |
| Key management | `PassphraseVault` y `KeystoreWalletKeys` por cuenta, sin cambio |
| Permissions | Solo la app |
| Rotation | Una frase nueva cuando la copia se regenera |
| Recovery | Sin la llave la copia se descarta y se vuelve a descargar |
| Failure behavior | Si la base no abre, la app muestra el aviso sin conexión sin lista |
| Tests | TC-12 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave del Keystore que envuelve la frase de la billetera (sin cambio) | Android Keystore, no exportable | La app | Todos | Con la regeneración de la copia | Borrar la copia y la llave al cerrar sesión | No aplica; el gate de secretos para el código |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | La cuenta y el documento salen del token, nunca del cliente |
| Secure authentication | NO | — | Sin cambio |
| Session management | NO | — | Usa la sesión existente y su renovación con un 401 |
| Token expiration | NO | — | Sin cambio |
| Revocation | NO | — | Sin cambio |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | Póliza del titular; solicitudes por `user_id` |
| Roles | YES | CTL-01 | Solo insured |
| Privilege separation | YES | CTL-01 | El token de solo reporte no sirve aquí |
| Brute-force protection | NO | — | Los números de póliza no son secretos; el dueño se verifica en cada petición |
| Rate limiting | YES | CTL-03 | 5 solicitudes por hora por asegurado, contadas en serie; la búsqueda no tiene límite (RISK-04) |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | YES | CTL-01 | Póliza ajena, inexistente o vencida responden igual |
| CSRF protection | NO | — | Endpoints con bearer, sin cookies |

## 13. Data isolation and multi-tenancy

N/A: el asegurado no es un socio; el aislamiento entre asegurados lo cubre CTL-01 y la llave de idempotencia lleva
el espacio de cada asegurado (TC-05).

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | Ninguna nueva; `LocationManager`, intents de Android y SQLCipher ya presentes | Sin cambio | app y backend | Ubicación, intents y copia cifrada | LOW | Revisión manual 2026-11-15 | Sin dependencias nuevas; sin SDK de mapas | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | `lat` y `lng` viajaban en la URL de `GET /insured/providers` y podían quedar en los registros de acceso del ALB o de CloudFront | THR-04 | LOW | MEDIUM | LOW | MITIGATE | Cerrado: la búsqueda es `POST /insured/providers/search` con la ubicación en el cuerpo, la ruta `GET` ya no existe y una prueba de la app comprueba que la URL no lleva coordenadas | MITIGATED |
| RISK-02 | En producción no hay catálogo real ni número de la línea de Solventa: la función no sirve allí | THR-06 | HIGH | MEDIUM | HIGH | MITIGATE | Alto mientras falte el catálogo del operador. Mitigación aplicada: en release, sin número, «Asistencia» no aparece en la billetera, así que el asegurado no llega a una lista vacía ni a números ficticios, y ningún texto manda a llamar a una línea que no existe; no se oculta por catálogo vacío porque la búsqueda exige ubicación o ciudad. Queda abierto hasta cargar el catálogo real y el número (DEC-01) | OPEN |
| RISK-03 | Las solicitudes, con su descripción libre y el servicio, no tienen retención ni purga definidas | THR-04 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio; la app pide no escribir datos de salud; propuesta: definir la retención con el responsable de datos | OPEN |
| RISK-04 | La búsqueda de prestadores no tiene límite por usuario o IP | THR-05 | LOW | LOW | LOW | MITIGATE | Bajo; la consulta es acotada y en memoria; propuesta: límite en el borde | OPEN |
| RISK-05 | No hay despacho real al prestador: el evento `AssistanceRequested` se ignora y nadie avisa al prestador. Si se carga un catálogo real, un asegurado en una emergencia podría creer que la ayuda ya va en camino: riesgo físico | THR-07 | MEDIUM | HIGH | HIGH | MITIGATE | Alto. Mitigación aplicada: la solicitud queda RECEIVED, la respuesta trae un prestador sugerido y un tiempo estimado si lo contacta, y la app pide llamarlo para coordinar, con «Llamar a …» como acción principal y sin decir asignado ni en camino. Propuesta: integrar el despacho con el operador de asistencia antes de cargar un catálogo real | OPEN |
| RISK-06 | La huella SHA-256 del cuerpo en `idempotency_records` incluía las coordenadas y, con el resto conocido, se podían recuperar | THR-04 | LOW | LOW | LOW | MITIGATE | Cerrado: la huella del espacio `insured:` se calcula sin `lat` ni `lng` (el canal B2B no cambia) y la tarea diaria borra los registros de más de 24 h | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Origen del catálogo real de prestadores y del número de la línea de Solventa para producción (RISK-02) | Carga desde el operador de asistencia; administración desde el portal; mantener la función solo fuera de producción | Mantener la función fuera de producción hasta tener el catálogo del operador | Product owner y Juan Camilo Peña | NO | PENDING |
