# S10.3 — Verificación y evidencias

**CI/CD CODE: IMPLEMENTED**.
**CI/CD CLOUD: PENDING MANUAL CONFIGURATION**.

## Validación local

Resultados locales del 9 de octubre de 2026, en `feature/restaurant-api-v1`.
No equivalen a GitHub Actions ejecutado ni a Azure validado.

| Comprobación | Estado / evidencia |
| --- | --- |
| Auditoría previa | Completada; no existían `.github`/workflows ni Sonar configurado. |
| Sintaxis y estructura YAML | APROBADA: ambos archivos parseados con SnakeYAML 2.5 ya disponible en el cache Maven, sin claves duplicadas ni tabulaciones; scripts extraídos validados con `bash -n`. |
| Expresiones, condiciones, needs, environments y permisos | REVISADOS localmente: referencias de jobs existentes, condiciones push/PR, gates de variables, environments `qa`/`production`, `contents: read`. Sin actionlint instalado; no sustituye la ejecución real en GitHub. |
| `mvn clean test --no-transfer-progress` | BUILD SUCCESS; log local ignorado `s10-3-test.log`. |
| Número de pruebas normales | 344 ejecutadas, 0 fallos, 0 errores, 0 omitidas en cada comando. |
| `mvn clean verify --no-transfer-progress` | BUILD SUCCESS; log local ignorado `s10-3-verify.log`; JAR `target/restaurante-0.0.1-SNAPSHOT.jar`. |
| Reporte `target/site/jacoco/` | GENERADO: HTML y XML; artifacts previstos en ambos workflows. |
| `git diff --check` | APROBADO, sin errores de whitespace. |

Maven necesitó ejecución fuera del sandbox para acceder al repositorio local de
dependencias; el primer intento restringido falló antes de ejecutar tests.
Las ejecuciones posteriores aprobadas produjeron los resultados anteriores.
El parser temporal se guardó bajo `target/cicd-validation/`, ignorado por Git;
no se instalaron herramientas globales ni se modificó `pom.xml`.

Se comprobó que los YAML no activan IT reales, no imprimen secretos y no
incluyen `.env` en artifacts. El único login Docker Hub QA está condicionado a
push; el build QA publica únicamente con ese evento. Azure QA requiere push
a `develop`/`main` y su flag; Azure PROD requiere el evento de tag válido y su
flag, con aprobación dependiente de la configuración manual del environment.

## Checklist cloud — todo pendiente hasta validación manual

| Evidencia | Estado |
| --- | --- |
| GitHub Secrets configurados y scopes correctos | PENDIENTE |
| Environment `qa` creado | PENDIENTE |
| Environment `production` creado | PENDIENTE |
| Required reviewer production configurado y protección comprobada | PENDIENTE |
| Recursos Azure QA y PROD separados | PENDIENTE |
| App Settings/Key Vault, JDBC TLS y Mongo TLS de cada ambiente | PENDIENTE |
| Variables `AZURE_QA_DEPLOY_ENABLED` / `AZURE_PROD_DEPLOY_ENABLED` habilitadas cuando corresponda | PENDIENTE |
| PR valida tests/verify/build sin login/push/deploy | PENDIENTE |
| Pipeline QA verde en GitHub Actions | PENDIENTE |
| Job `test` QA y artifact JaCoCo | PENDIENTE |
| Docker build/push QA | PENDIENTE |
| Tag Docker `qa` visible | PENDIENTE |
| Tag Docker `sha-<commit>` visible | PENDIENTE |
| Azure deploy QA, job no omitido | PENDIENTE |
| Smoke OpenAPI QA exitoso | PENDIENTE |
| QA Swagger público HTTPS | PENDIENTE |
| Tag Git nuevo `vX.Y.Z` autorizado/publicado | PENDIENTE |
| Pipeline PROD y Maven verify exitosos | PENDIENTE |
| Pausa de aprobación manual production | PENDIENTE |
| Aprobación registrada por reviewer | PENDIENTE |
| Azure deploy PROD, job no omitido | PENDIENTE |
| PROD Swagger público HTTPS | PENDIENTE |
| Imagen versionada y `latest` visibles en Docker Hub | PENDIENTE |
| Login/roles y persistencia/auditoría en QA y PROD | PENDIENTE |
| Diagrama de despliegue dibujado manualmente | PENDIENTE |
| Capturas de workflows, aprobación, Docker Hub, Azure y Swagger | PENDIENTE |

## Procedimiento de evidencia

Validar primero PR, luego push QA con Docker Hub configurado y deployment
habilitado. Guardar enlaces reales al run, jobs, artifact y digest publicado;
confirmar que QA no modifica los tags `1.0.0`/`latest`.

Cuando recursos y protección production estén preparados, usar una nueva
versión final autorizada. Registrar verify, tags Docker, estado **Waiting** del
job de deploy, reviewer/aprobación y despliegue versionado. Un build publicado
o un workflow verde con Azure omitido no prueba deployment ni aprobación.

Tras el deploy, abrir `/swagger-ui/index.html` y `/v3/api-docs` en las URLs
HTTPS reales. El smoke automático QA no requiere credenciales; la prueba
manual autenticada debe mantener JWT/passwords fuera de logs y capturas.
Comprobar persistencia PostgreSQL y eventos Mongo en el ambiente correspondiente.

Añadir ubicación/enlace de cada captura y del diagrama manual solo después de
obtenerlos. No adjuntar `.env`, JSON Azure credentials, URI Mongo ni secrets.
Actualizar QA/PROD del README cuando se hayan validado; actualmente **PENDING**.

En esta fase no se hace commit, push, merge, rebase, reset, tag, publicación
Docker Hub, ejecución cloud ni creación de recursos. La validación Docker
local de S10.2 es previa; no demuestra que los nuevos workflows hayan corrido.
