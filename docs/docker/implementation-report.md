# S10.2 — Reporte de implementación Docker

Fecha de cierre: 2026-10-06.

**S10.2 DOCKERIZACIÓN: COMPLETADA Y VALIDADA.**

## Arquitectura y construcción

El stack Compose reúne la API Spring Boot 3.5.16 con Java 21, PostgreSQL 16
para persistencia transaccional y MongoDB 7 para auditoría secundaria.
La API utiliza el perfil `docker` y se comunica con las bases mediante
los nombres de servicio `postgres` y `mongo` en una red dedicada.
PostgreSQL es la fuente de verdad; los eventos Mongo se escriben después
del commit PostgreSQL, conservando el historial RN-07 en JPA.

El Dockerfile es multistage:

1. Builder `maven:3.9-eclipse-temurin-21`: copia primero `pom.xml`, resuelve
   dependencias con `dependency:go-offline` para aprovechar caché, copia fuentes
   y ejecuta `clean package -DskipTests`. Las pruebas se ejecutan por separado.
2. Runtime `eclipse-temurin:21-jre-alpine`: recibe únicamente el JAR del builder
   como `/app/app.jar` y arranca con `java -jar`.

La imagen crea el usuario y grupo `app`, asigna la propiedad del JAR y fija
`USER app`: ejecución configurada como usuario **no-root**. Esto se constata
en el Dockerfile; no se presenta como una nueva comprobación con `exec id`.
El contexto Docker excluye `target`, `src/test`, configuración privada,
certificados, logs y documentación mediante `.dockerignore`.

## Servicios, puertos y healthchecks

| Servicio | Imagen | Contenedor | Puerto interno | Puerto host |
| --- | --- | --- | --- | --- |
| API | `american-bites-api:local`, construida desde Dockerfile | `american-bites-api` | 8080 | 8080 |
| PostgreSQL | `postgres:16-alpine` | `american-bites-postgres` | 5432 | 5433 por defecto, configurable |
| MongoDB | `mongo:7` | `american-bites-mongo-compose` | 27017 | 27018 por defecto, configurable |

PostgreSQL usa `pg_isready` con la base y usuario configurados: intervalo 10 s,
timeout 5 s, 10 reintentos y periodo inicial 20 s. Mongo usa `mongosh` con
autenticación en `admin` y `ping`: intervalo 10 s, timeout 10 s, 10 reintentos
y periodo inicial 30 s. La API depende de ambos con `service_healthy`.
No tiene healthcheck propio: su estado Up se complementó con Swagger,
login y operaciones HTTP reales.

Los tres servicios usan la red bridge `american-bites-net`.
La API accede a PostgreSQL por `postgres:5432` y a Mongo por `mongo:27017`.
La API está disponible en `http://localhost:8080` y Swagger en
`http://localhost:8080/swagger-ui/index.html`.

## Volúmenes y variables

- `postgres-data`: montado en `/var/lib/postgresql/data`.
- `mongo-data`: montado en `/data/db`.

Son volúmenes nombrados, con prefijo del proyecto Compose. Conservar el mismo
proyecto permite reutilizarlos. **NO usar `docker compose down -v` si se desea
conservar la información.**

La configuración se suministra mediante variables de entorno y un archivo
privado ignorado por Git, sin incorporar valores secretos a la documentación.

| Grupo | Configuración |
| --- | --- |
| Aplicación | Perfil `docker`, puerto 8080 y SSL desactivado para HTTP local. |
| PostgreSQL | Host `postgres`, puerto 5432; `DB_NAME` y `DB_USER` con valores predeterminados; contraseña obligatoria sin valor predeterminado. |
| MongoDB | Usuario y contraseña de inicialización obligatorios; URI autenticada suministrada externamente con host `mongo`, puerto 27017, base `american_bites` y autenticación en `admin`. |
| JWT | Clave de firma obligatoria, Base64 de al menos 32 bytes decodificados; `JWT_EXPIRATION_MS` positivo, predeterminado 3600000. |
| Bootstrap | Alta GERENTE opcional si se definen conjuntamente email y contraseña y el usuario no existe. |
| Puertos host | `POSTGRES_HOST_PORT` y `MONGO_HOST_PORT`, predeterminados 5433 y 27018. |

Las variables de inicialización de bases no cambian las credenciales de un
volumen ya inicializado. No se registran passwords, tokens, claves de firma,
hashes, URI Mongo real ni contenido de la configuración privada.

## Prueba funcional real

Según la validación manual realizada, se aprobaron la configuración Compose,
build de la API, arranque del stack, Swagger y conexiones reales desde la API
Docker a PostgreSQL y Mongo. `docker compose ps` mostró API Up y ambas bases
healthy. Se aprobaron login GERENTE, JWT dentro del contenedor y acceso a un
endpoint protegido.

`BeforeRestart` creó `pedidoId = 1` y guardó en PostgreSQL tres transiciones:

1. `RECIBIDO -> EN_PREPARACION`.
2. `EN_PREPARACION -> LISTO`.
3. `LISTO -> ENTREGADO`.

En `american_bites.eventos_restaurante` se encontraron exactamente **3**
documentos para `entidadTipo = Pedido`, `entidadId = 1`, todos de tipo
`CAMBIO_ESTADO_PEDIDO`. Cada documento conserva entidad, usuario, timestamp,
estado anterior y estado nuevo. `countDocuments` devolvió **3** y las
transiciones coinciden con PostgreSQL.

## Restart y persistencia

Se ejecutó `docker compose restart api`. Después, la API seguía Up y las bases
PostgreSQL y Mongo healthy. `AfterRestart` recuperó las mismas tres transiciones
y el script reportó persistencia tras reinicio y reapertura verificadas.

La persistencia PostgreSQL está validada tras reiniciar la API; Mongo continuó
disponible. El alcance comprobado es el restart exclusivo de la API, sin borrar
datos ni reiniciar o recrear las bases o sus volúmenes.

## Docker Hub

**COMPLETADO.** Imagen pública:
[danielvalero09/american-bites-api](https://hub.docker.com/r/danielvalero09/american-bites-api).
Tags publicados: **`1.0.0`** y **`latest`**.
Publicación verificada manualmente; no se realizan nuevos pushes durante el cierre.

## Verificación final y evidencias

Los resultados de `mvn clean test`, `mvn clean verify`,
`docker compose config --quiet`, `git diff --check` y `git status --short`
se registran en [verification.md](verification.md).
Las suites opt-in de bases reales y los smoke tests no se repiten.

Solo quedan pendientes las capturas todavía no guardadas en el repositorio,
detalladas en ese documento, sin nombres ni rutas inventados.
CI/CD S10.3 y Kubernetes están fuera del alcance de S10.2.

Este cierre modifica únicamente documentación: README, verificación y reporte.
No se modifica código productivo, Dockerfile, Compose ni configuración;
no se ejecutan commit, push, merge, rebase, reset ni borrado de datos Docker.

**S10.2 DOCKERIZACIÓN: COMPLETADA Y VALIDADA.**
