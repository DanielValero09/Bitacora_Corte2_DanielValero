# Azure — preparación manual pendiente

QA URL: **PENDING**. PROD URL: **PENDING**. No se han creado ni comprobado
recursos, Service Principals, credenciales o URLs Azure en esta fase.

## Recursos por ambiente

| QA | PROD |
| --- | --- |
| App Service Linux / Web App for Containers QA y un App Service Plan compatible. | App Service Linux / Web App for Containers PROD y capacidad/plan definido. |
| PostgreSQL Flexible Server QA, base y usuario propios. | PostgreSQL Flexible Server PROD, base y usuario propios. |
| Servicio Mongo compatible QA, base y usuario propios. | Servicio Mongo compatible PROD, base y usuario propios. |

Preparar manualmente grupos de recursos, región, redes, firewall/DNS, capacidad,
backups y acceso desde cada App Service. No reutilizar bases, contraseñas,
JWT_SECRET ni identidades QA/PROD. No es necesario desplegar el Compose local
en App Service: cada ambiente ejecuta el contenedor API y conecta con servicios
de persistencia independientes.

Mongo puede ser Azure Cosmos DB for MongoDB u otro servicio MongoDB accesible
desde Azure. No se presupone que Cosmos exista ni que cualquier configuración
de Cosmos sea compatible sin comprobarla. Validar operaciones del driver,
autenticación, TLS y requisitos del proveedor; guardar las URI reales en
`MONGODB_URI_QA` y `MONGODB_URI_PROD` o en los almacenes Azure respectivos,
y configurar `MONGODB_URI` en cada App Service.

## Contenedor, puerto y HTTPS

Preparar un **App Service Linux con contenedor personalizado único** compatible
con `azure/webapps-deploy@v3` y su entrada `images`. El repositorio público es
`danielvalero09/american-bites-api`; no necesita el token de escritura Docker
Hub en App Settings para hacer pull público. Configurar el registro Docker Hub
y la imagen en Deployment Center; el workflow actualizará la referencia.

El modo clásico usa `WEBSITES_PORT=8080`. `SERVER_PORT=8080` controla Spring;
`EXPOSE 8080` por sí solo no configura el routing Azure. Si se crea un recurso
con sidecars (`sitecontainers`), configurar el puerto target 8080 del contenedor
principal y verificar la ruta de despliegue correspondiente antes de habilitar
los workflows; no asumir que el contrato `images` equivale a configurar
sitecontainers. Esta fase prepara el modo de contenedor único. Consultar
[configuración oficial de contenedores](https://learn.microsoft.com/en-us/azure/app-service/configure-custom-container).

Activar HTTPS Only en Azure. App Service termina TLS público y reenvía al
contenedor por HTTP; mantener `SSL_ENABLED=false`. No subir el keystore local
ni activar el HTTPS autofirmado. Configurar
`SERVER_FORWARD_HEADERS_STRATEGY=framework` para procesar los headers del proxy
y comprobar los enlaces Swagger detrás de Azure. Si hay un frontend separado,
la lista CORS actual solo permite localhost: definir sus orígenes reales en una
fase posterior; Swagger del mismo origen no requiere ese cambio.

## App Settings QA y PROD

Configurar manualmente en cada App Service → Settings → Environment variables
/ Configuration, usando los valores reales de **ese** ambiente.

| App Setting | Fuente / requisito |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `docker`. |
| `SSL_ENABLED` | `false`; HTTPS lo ofrece Azure. |
| `SERVER_PORT` | `8080`. |
| `WEBSITES_PORT` | `8080` en modo clásico; en sidecars usar puerto target. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` detrás del proxy. |
| `DB_HOST` | `DB_HOST_QA` o `DB_HOST_PROD`, host real del servidor. |
| `DB_PORT` | `DB_PORT_QA` o `DB_PORT_PROD`, puerto real. |
| `DB_NAME` | `DB_NAME_QA` o `DB_NAME_PROD`. |
| `DB_USER` | `DB_USER_QA` o `DB_USER_PROD`. |
| `DB_PASSWORD` | `DB_PASSWORD_QA` o `DB_PASSWORD_PROD`; sensible. |
| `MONGODB_URI` | URI del ambiente correspondiente; sensible, con TLS según proveedor. |
| `JWT_SECRET` | Clave del ambiente correspondiente; Base64 de al menos 32 bytes aleatorios decodificados. |
| `JWT_EXPIRATION_MS` | Duración positiva; el default actual es `3600000`. Definir explícitamente la política del ambiente. |
| `BOOTSTRAP_ADMIN_EMAIL` | Opcional: primer GERENTE del ambiente. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Opcional junto con email; máximo 72 bytes UTF-8. Retirar después de inicializar. |
| `SPRING_DATASOURCE_URL` | Override cloud para JDBC/TLS descrito a continuación. |

Las variables sensibles pueden ser referencias a Azure Key Vault con identidad
administrada y permisos de lectura adecuados; alternativamente, App Settings
gestionados con acceso restringido. Los YAML no contienen valores runtime ni
los imprimen. Los secrets GitHub `_QA`/`_PROD` no son variables automáticas Azure.

El perfil Docker actual no agrega parámetros TLS a JDBC. Para Azure, conservar
el mismo archivo y definir **solo en App Settings** la URL completa con hostname
real, base real y `sslmode=verify-full`. Instalar/referenciar la CA requerida por
el servidor mediante `sslrootcert` y una ruta accesible al usuario `app`; validar
la cadena y hostname con el controlador PostgreSQL. La estructura a completar
es `jdbc:postgresql://<host-real>:<puerto-real>/<base-real>?sslmode=verify-full&sslrootcert=<ruta-CA>`.
No incluir password en la URL. `SPRING_DATASOURCE_URL` tiene precedencia sobre
la propiedad del perfil; conservar `DB_HOST`/`DB_PORT`/`DB_NAME` coherentes.
La verificación TLS y distribución/rotación de CA son pendientes manuales,
según la [documentación JDBC PostgreSQL](https://jdbc.postgresql.org/documentation/ssl/).

La aplicación mantiene `ddl-auto=update` y SQL logging de la fase anterior.
Antes de producción decidir migraciones/backups y, si corresponde, usar
`SPRING_JPA_HIBERNATE_DDL_AUTO` y `SPRING_JPA_SHOW_SQL` como overrides Azure.
No se cambia el comportamiento local en esta fase.

## AZURE_CREDENTIALS — identidad manual

`azure/login@v2` recibe el JSON de un Service Principal con los campos
`clientId`, `clientSecret`, `subscriptionId` y `tenantId`. La identidad debe tener
solo los permisos necesarios para actualizar el App Service correspondiente;
limitar el scope al recurso o grupo de recursos de ese ambiente. Preparar una
identidad distinta QA/PROD, guardar ambas bajo el nombre `AZURE_CREDENTIALS` en
sus environments, y definir vencimiento/rotación de sus secretos.

Procedimiento manual en Portal/CLI: seleccionar la suscripción real, registrar
la aplicación/Service Principal de cada ambiente, asignar RBAC en el scope real,
crear la credencial y componer el JSON dentro de un gestor seguro. Transferirlo
al secreto GitHub del environment correspondiente. Si se utiliza CLI, consultar
`az ad sp create-for-rbac --help` y controlar destino/salida de credenciales;
no ejecutar un comando que vuelque el JSON secreto al terminal o a logs CI.
No se crean identidades ni se entregan IDs ficticios en esta fase. Ver el
[contrato oficial de Azure Login](https://github.com/Azure/login#login-with-a-service-principal-secret).

Este flujo usa credenciales clásicas, sin OIDC y sin permiso GitHub
`id-token: write`. `AZURE_CORE_OUTPUT=none` limita salida Azure CLI de los jobs.

## Orden para habilitar el despliegue

1. Crear los recursos reales y conexiones TLS, con aislamiento QA/PROD.
2. Configurar App Settings/Key Vault, puerto, HTTPS y pull de la imagen pública.
3. Crear identidades Azure, secrets Docker Hub y secrets de cada environment.
4. Crear `qa` sin revisión; crear `production` con Required reviewers y verificar
   que la política se aplica a tags finales.
5. Habilitar `AZURE_QA_DEPLOY_ENABLED` en variables de repositorio y validar
   un push autorizado a `develop`/`main` mediante la checklist.
6. Habilitar `AZURE_PROD_DEPLOY_ENABLED` solamente después de validar QA y la
   protección de production. Publicar una versión nueva cuando esté autorizado.
7. Tras cada deployment, comprobar Swagger/OpenAPI por HTTPS, login/roles,
   PostgreSQL y auditoría Mongo; guardar evidencias sin secretos.

El smoke automático QA verifica OpenAPI público. PROD requiere comprobación
remota manual por HTTPS usando la URL real de Overview; no se presupone su host.
Actualizar las URLs del README solo después de crear y validar los App Services.
