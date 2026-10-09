# GitHub Secrets — nombres, propósito y scope

Configuración manual pendiente. No hay valores reales en este documento.

## Repositorio / global

Crear en GitHub repository → Settings → Secrets and variables → Actions.
Los jobs de build no usan environments, por lo que necesitan los secretos
Docker Hub a nivel de repositorio o de organización con acceso a este repo.

| Nombre | Propósito | Scope |
| --- | --- | --- |
| `DOCKERHUB_USERNAME` | Cuenta autorizada para escribir en `danielvalero09/american-bites-api`. | Repositorio / organización. |
| `DOCKERHUB_TOKEN` | Personal Access Token para login y push Docker Hub. | Repositorio / organización. |
| `AZURE_CREDENTIALS` | JSON de identidad Azure usado por `azure/login`. | Nombre global; preferir un secreto distinto con este mismo nombre en cada environment. |

No compartir identidad ni credenciales Azure entre QA y PROD. Evitar una
identidad global con acceso a ambos ambientes: `qa` y `production` pueden
sobrescribir el nombre `AZURE_CREDENTIALS` con sus propios secretos.

## QA — environment `qa`

| Nombre | Propósito | Scope |
| --- | --- | --- |
| `AZURE_WEBAPP_NAME_QA` | Nombre real del App Service QA utilizado por el workflow. | `qa`. |
| `JWT_SECRET_QA` | Clave de firma JWT QA; cargar como `JWT_SECRET` en Azure. | `qa` o almacén Azure QA. |
| `DB_HOST_QA` | Host PostgreSQL QA; cargar como `DB_HOST`. | `qa` o configuración Azure QA. |
| `DB_PORT_QA` | Puerto PostgreSQL QA; cargar como `DB_PORT`. | `qa` o configuración Azure QA. |
| `DB_NAME_QA` | Base PostgreSQL QA; cargar como `DB_NAME`. | `qa` o configuración Azure QA. |
| `DB_USER_QA` | Usuario PostgreSQL QA; cargar como `DB_USER`. | `qa` o almacén Azure QA. |
| `DB_PASSWORD_QA` | Password PostgreSQL QA; cargar como `DB_PASSWORD`. | `qa` o almacén Azure QA. |
| `MONGODB_URI_QA` | URI autenticada Mongo QA; cargar como `MONGODB_URI`. | `qa` o almacén Azure QA. |
| `BOOTSTRAP_ADMIN_EMAIL_QA` | Email opcional del primer GERENTE QA. | `qa` o almacén Azure QA. |
| `BOOTSTRAP_ADMIN_PASSWORD_QA` | Password opcional del primer GERENTE QA. | `qa` o almacén Azure QA. |

## PROD — environment `production`

| Nombre | Propósito | Scope |
| --- | --- | --- |
| `AZURE_WEBAPP_NAME_PROD` | Nombre real del App Service PROD utilizado por el workflow. | `production`. |
| `JWT_SECRET_PROD` | Clave de firma JWT PROD; cargar como `JWT_SECRET` en Azure. | `production` o almacén Azure PROD. |
| `DB_HOST_PROD` | Host PostgreSQL PROD; cargar como `DB_HOST`. | `production` o configuración Azure PROD. |
| `DB_PORT_PROD` | Puerto PostgreSQL PROD; cargar como `DB_PORT`. | `production` o configuración Azure PROD. |
| `DB_NAME_PROD` | Base PostgreSQL PROD; cargar como `DB_NAME`. | `production` o configuración Azure PROD. |
| `DB_USER_PROD` | Usuario PostgreSQL PROD; cargar como `DB_USER`. | `production` o almacén Azure PROD. |
| `DB_PASSWORD_PROD` | Password PostgreSQL PROD; cargar como `DB_PASSWORD`. | `production` o almacén Azure PROD. |
| `MONGODB_URI_PROD` | URI autenticada Mongo PROD; cargar como `MONGODB_URI`. | `production` o almacén Azure PROD. |
| `BOOTSTRAP_ADMIN_EMAIL_PROD` | Email opcional del primer GERENTE PROD. | `production` o almacén Azure PROD. |
| `BOOTSTRAP_ADMIN_PASSWORD_PROD` | Password opcional del primer GERENTE PROD. | `production` o almacén Azure PROD. |

Los workflows consumen únicamente los dos secretos Docker Hub,
`AZURE_CREDENTIALS` y el nombre del App Service del environment correspondiente.
Los nombres con sufijo `_QA`/`_PROD` son el inventario de provisión de aplicación:
**crear estos secretos en GitHub no configura Azure automáticamente**. Elegir
App Settings/Key Vault como fuente runtime y evitar duplicarlos en GitHub si
no se implementa después una sincronización. En Azure, usar nombres sin sufijo.
`JWT_EXPIRATION_MS` se configura como App Setting, no como credencial.

## Token Docker Hub

Procedimiento manual: Docker Hub → Account Settings → Security → Personal
Access Token. En la interfaz actual puede aparecer directamente como Personal
access tokens dentro de Account Settings. Nombre sugerido: `github-actions`.
Permisos: **Read & Write**. Guardarlo como `DOCKERHUB_TOKEN` y conservarlo en un
gestor seguro. No usar la contraseña normal de Docker Hub ni generar el token
automáticamente. Ver la
[guía oficial de PAT](https://docs.docker.com/security/access-tokens/personal-access-tokens/).

## Environments

1. Settings → Environments → New environment → `qa`.
2. Agregar el nombre real del App Service QA y su `AZURE_CREDENTIALS` aislado,
   además de los secretos QA si se decide administrarlos desde GitHub.
3. Permitir despliegues desde `develop` y `main`; no exigir revisión manual QA.
4. Settings → Environments → New environment → `production`.
5. Configurar **Required reviewers**, guardar y comprobar la protección.
   Restringir deployment tags a `v*.*.*`; impedir autoaprobación y bypass
   administrativo si las opciones están disponibles y se requiere aprobación
   por otra persona.
6. Agregar nombre real del App Service PROD y su propia `AZURE_CREDENTIALS`.

GitHub aplicará la pausa antes de ejecutar `deploy-prod` y liberar sus secrets.
No se afirma que estos environments ya existan. Verificar disponibilidad de
Required reviewers en el plan/visibilidad actual antes de habilitar PROD.
