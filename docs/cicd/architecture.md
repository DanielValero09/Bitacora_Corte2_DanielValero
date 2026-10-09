# S10.3 — Arquitectura CI/CD

Estado de esta fase local:

- **CI/CD CODE: IMPLEMENTED**.
- **CI/CD CLOUD: PENDING MANUAL CONFIGURATION**.
- QA URL: **PENDING**. PROD URL: **PENDING**.

## Auditoría previa

La rama de trabajo es `feature/restaurant-api-v1`. El árbol estaba limpio al
iniciar. No existía `.github` ni workflows que conservar o reemplazar. No se
encontró una estrategia de ramas formal ni configuración Sonar en el POM o la
documentación actual. Hay pruebas JUnit/Mockito/MockMvc, auditorías anteriores
y JaCoCo 0.8.14; no hay un quality gate Sonar configurado.

Se inspeccionaron `pom.xml`, `Dockerfile`, `docker-compose.yml`,
`application.properties`, `application-docker.properties`, `SecurityConfig`,
`JwtUtil`, `.gitignore`, `.dockerignore` y `README.md` antes de escribir workflows.
Java es 21 y Spring Boot 3.5.16. El JAR real es
`target/restaurante-0.0.1-SNAPSHOT.jar`. El Dockerfile compila con Maven y
`-DskipTests`, usa JRE 21, usuario no root y HTTP 8080. Las pruebas se ejecutan
antes de construir la imagen. Compose local usa `american-bites-api:local`;
el repositorio remoto existente es `danielvalero09/american-bites-api`.

## Continuous Integration

El job `test` usa Ubuntu, checkout v4, Temurin 21 y cache Maven. Ejecuta
`mvn clean verify --no-transfer-progress`: el ciclo Maven ya incluye `test`,
`package` y el reporte JaCoCo ligado a `test`. Ejecutar además `clean test`
dentro de CI duplicaría innecesariamente la suite. En la validación local de
esta fase sí se ejecutan ambos comandos solicitados por separado.

Se publica únicamente `target/site/jacoco/` cuando existe, mediante
`actions/upload-artifact@v4`, también si hay un fallo posterior. No se suben
`.env`, certificados ni configuración privada. Las pruebas normales no usan
servicios PostgreSQL/Mongo reales. No se activan `s09.postgres`, `s09.mongo`
ni `s10.postgres`; las clases de integración `*IT` son opt-in.

`build-qa` depende de `test` y valida el Dockerfile real con Buildx. Los PR a
`develop`/`main` ejecutan tests, verify y build sin login, push o despliegue.
El evento usado es `pull_request`, nunca `pull_request_target`.

## Continuous Delivery

| Evento | Resultado después de verify |
| --- | --- |
| Push a `develop` o `main` | Build/push de `qa` y `sha-<12 caracteres del commit>`; luego deploy QA si está habilitado. |
| PR a `develop` o `main` | Solo validación; no consulta secretos ni publica imágenes. |
| Push de tag `vX.Y.Z` | Build/push de `X.Y.Z` y `latest`; luego aprobación y deploy PROD si está habilitado. |

El filtro `v*.*.*` es un glob, por eso PROD valida además el formato estricto
`vX.Y.Z`, sin prerelease, sufijos ni ceros iniciales. Un tag inválido o verify
fallido impide llegar al login/build/push. `docker/metadata-action@v5` extrae la
versión del tag. La versión Maven del JAR puede seguir siendo `0.0.1-SNAPSHOT`;
la versión de distribución de la imagen procede del tag Git.

QA nunca modifica `1.0.0` ni `latest`. PROD no usa `qa` y despliega la versión
final; `latest` se publica antes de aprobar el deploy, según el flujo solicitado.
No reutilizar tags de release ni sobrescribir imágenes versionadas.

## Habilitación cloud pendiente

Crear manualmente estas **variables de repositorio**, en Settings → Secrets
and variables → Actions → Variables, únicamente después de preparar recursos,
App Settings, identidades y environments:

| Variable de control | Activación |
| --- | --- |
| `AZURE_QA_DEPLOY_ENABLED` | Valor literal `true` habilita `deploy-qa`. |
| `AZURE_PROD_DEPLOY_ENABLED` | Valor literal `true` habilita `deploy-prod`, después de configurar Required reviewers. |

Ausentes o con otro valor, se omite el respectivo job Azure. Esto permite
preparar CI y Docker Hub antes de tener Azure; **un deploy omitido no demuestra
un despliegue exitoso**. Estas variables no desactivan el push de imágenes:
un push a ramas QA o un tag final publicará si CI pasa y Docker Hub está
configurado. Esta implementación no ejecuta eventos remotos.

`deploy-qa` depende de `build-qa`, solo acepta push a las dos ramas y usa
`environment: qa`. `deploy-prod` depende de `build-prod` y usa
`environment: production`. La pausa se aplica por Required reviewers en GitHub,
no por una simulación YAML. Sin esa protección, el nombre del environment
por sí solo no garantiza aprobación. Verificar que el plan/visibilidad del
repositorio permite esta función antes de habilitar PROD, según la
[referencia oficial de environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).

## Aislamiento, identidad y concurrencia

QA y PROD necesitan App Services diferentes, bases PostgreSQL diferentes,
usuarios/passwords diferentes, bases o instancias Mongo diferentes y
`JWT_SECRET` distintos. Nunca compartir credenciales QA/PROD. El mismo nombre
`AZURE_CREDENTIALS` puede almacenarse en cada environment con una identidad
distinta y alcance mínimo; GitHub resuelve el secreto del environment del job.
Docker Hub es el canal común de distribución, no una base de datos compartida.

El namespace y el username Docker Hub se fijan en `danielvalero09`. El username
es público y no requiere un secreto `DOCKERHUB_USERNAME`; configurar el secreto
`DOCKERHUB_TOKEN` con permisos de escritura sobre ese repositorio.
Los secretos de aplicación se configuran
manualmente en App Settings/Key Vault; los YAML no los trasladan automáticamente.

QA cancela ejecuciones anteriores por ref y serializa los jobs de despliegue al
App Service compartido. La imagen QA lleva `:qa@<digest>` para fijar el build de
esa ejecución aunque otra rama actualice el alias `qa`. Entre `main` y `develop`
no existe orden total de commits: el último despliegue exitoso será el activo;
coordinar pushes si ambas ramas apuntan al mismo QA. La cancelación de una
ejecución no revierte una actualización Azure que ya haya sido enviada.

PROD usa concurrencia global con `cancel-in-progress: false`, para no interrumpir
una release esperando aprobación o ya aprobada. GitHub puede reemplazar una
ejecución pendiente en un mismo grupo: publicar una release a la vez y comprobar
la cola, según la
[documentación de concurrencia](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).

Ambos workflows tienen solo `contents: read`. La autenticación es clásica
con `AZURE_CREDENTIALS`, sin OIDC ni `id-token: write`. No se imprimen secretos
ni se pasan credenciales de runtime al build Docker.

## Smoke y entregables

`SecurityConfig` permite `/v3/api-docs` sin token. Después de un deploy QA real,
el workflow consulta esa ruta por HTTPS con reintentos y valida el JSON OpenAPI.
Usa la URL devuelta por Azure, sin construir un host ficticio. No imprime el
body, no necesita JWT y no añade un health endpoint. Esto comprueba arranque y
OpenAPI; no sustituye las pruebas manuales de persistencia, roles o Mongo.

Reutilizar `application-docker.properties` para local, QA y PROD con variables
diferentes. No se crean perfiles `qa`/`prod` ni se modifica Docker local.
Consultar [secrets](github-secrets.md), [Azure](azure-setup.md),
[verificación](verification.md) y la
[especificación para el diagrama manual](deployment-diagram-spec.md).
