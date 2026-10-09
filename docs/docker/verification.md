# S10.2 — Verificación Docker

Fecha de cierre: 2026-10-06.

**S10.2 DOCKERIZACIÓN: COMPLETADA Y VALIDADA.**

Este documento registra los resultados de la validación manual real aportados
para el cierre. Las comprobaciones funcionales ya se realizaron y no se repiten
en esta actualización documental. Las capturas son evidencias adicionales:
su ausencia en el repositorio no deja pendiente una prueba ya aprobada.

## Validación manual real

| Comprobación | Estado | Resultado registrado |
| --- | --- | --- |
| `docker compose config --quiet` | APROBADO | Configuración Compose válida. |
| `docker compose build api` | APROBADO | Imagen de la API construida. |
| `docker compose up -d` | APROBADO | Stack iniciado. |
| `docker compose ps` | APROBADO | `american-bites-api`: Up; `american-bites-postgres`: healthy; `american-bites-mongo-compose`: healthy. |
| Perfil activo | APROBADO | `docker`. |
| API | APROBADO | Disponible en `http://localhost:8080`. |
| Swagger | APROBADO | `http://localhost:8080/swagger-ui/index.html`. |
| PostgreSQL 16 | APROBADO | Conexión real desde la API Docker. |
| MongoDB 7 | APROBADO | Conexión real desde la API Docker mediante `mongo:27017`. |
| Login GERENTE | APROBADO | Autenticación real satisfactoria. |
| JWT | APROBADO | Funcionamiento dentro del contenedor y acceso a endpoint protegido. |
| Smoke test `BeforeRestart` | APROBADO | Creó `pedidoId = 1` y registró tres transiciones en PostgreSQL. |
| `docker compose restart api` | APROBADO | Reinicio exclusivo de la API realizado. |
| `docker compose ps` después del restart | APROBADO | PostgreSQL healthy, Mongo healthy y API Up. |
| Smoke test `AfterRestart` | APROBADO | Recuperó las mismas tres transiciones; el script reportó persistencia tras reinicio y reapertura verificadas. |
| Persistencia PostgreSQL | APROBADO | Historial del pedido 1 conservado tras restart de API. |
| Auditoría Mongo | APROBADO | Exactamente tres documentos del pedido 1, con los campos de auditoría conservados. |
| Disponibilidad Mongo tras restart | APROBADO | Mongo continuó disponible tras reiniciar la API. |
| Docker Hub | COMPLETADO | Imagen pública y tags publicados; verificación manual realizada. |

## PostgreSQL y smoke tests

El smoke test `BeforeRestart` creó el pedido **1** y registró el historial:

| Transición | Estado anterior | Estado nuevo |
| --- | --- | --- |
| 1 | `RECIBIDO` | `EN_PREPARACION` |
| 2 | `EN_PREPARACION` | `LISTO` |
| 3 | `LISTO` | `ENTREGADO` |

**Total de transiciones: 3.** Tras ejecutar `docker compose restart api`,
`AfterRestart` recuperó las mismas tres transiciones y el script confirmó
persistencia tras reinicio y reapertura. Ambos smoke tests están **APROBADOS**.

## MongoDB

- Host interno validado: `mongo:27017`.
- Base: `american_bites`.
- Colección: `eventos_restaurante`.
- Entidad: `entidadTipo = Pedido`, `entidadId = 1`.
- Tipo de los tres documentos: `CAMBIO_ESTADO_PEDIDO`.
- Transiciones: `RECIBIDO -> EN_PREPARACION`, `EN_PREPARACION -> LISTO`
  y `LISTO -> ENTREGADO`.
- Cada documento conserva `entidadId`, `usuario`, `timestamp`,
  `estadoAnterior` y `estadoNuevo`.
- Resultado de `countDocuments` para el pedido 1: **3**.

La comprobación de los documentos acredita escritura real de auditoría
secundaria; no se limita al arranque del driver. PostgreSQL conserva el historial
RN-07 y sigue siendo la fuente de verdad.

## Volúmenes y alcance del reinicio

Compose define `postgres-data` en `/var/lib/postgresql/data` y `mongo-data`
en `/data/db`. La prueba realizada valida persistencia PostgreSQL tras restart
de la API y disponibilidad continuada de Mongo. El reinicio validado fue
exclusivamente de la API; no se atribuyen resultados a reinicios de bases
ni a eliminación o recreación de volúmenes.

**NO usar `docker compose down -v` si se desea conservar la información.**

## Docker Hub — COMPLETADO

Imagen pública:
[danielvalero09/american-bites-api](https://hub.docker.com/r/danielvalero09/american-bites-api).

Tags publicados y verificados manualmente:

- `1.0.0`.
- `latest`.

La publicación ya se completó. El cierre documental no requiere nuevos pushes.

## Verificación final del cierre

| Comando | Estado | Resultado en esta sesión |
| --- | --- | --- |
| `mvn clean test` | APROBADO | BUILD SUCCESS; 344 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| `mvn clean verify` | APROBADO | BUILD SUCCESS; 344 pruebas, 0 fallos, 0 errores, 0 omitidas; JAR ejecutable generado. |
| `docker compose config --quiet` | APROBADO | Código de salida 0; no se imprimió configuración interpolada. |
| `git diff --check` | APROBADO | Código de salida 0; sin errores de whitespace. Los documentos nuevos también se revisaron sin espacios finales. |
| `git status --short` | EJECUTADO | README modificado y artefactos Docker sin seguimiento, detallados abajo. |

El primer intento restringido de Maven falló por acceso al repositorio local;
las ejecuciones finales autorizadas fuera del sandbox terminaron satisfactoriamente.
Compose emitió una advertencia de acceso al archivo de configuración del cliente
Docker dentro del sandbox, sin impedir la validación. Git avisó de conversión
LF a CRLF para README, sin errores en `diff --check`.

Salida de `git status --short`:

```text
 M README.md
?? .dockerignore
?? .env.example
?? Dockerfile
?? docker-compose.yml
?? docs/docker/
?? src/main/resources/application-docker.properties
```

Los artefactos Docker sin seguimiento ya figuraban en el estado inicial;
en este cierre se actualizaron README y `docs/docker/verification.md` y se creó
`docs/docker/implementation-report.md`. No se añadieron archivos al índice.
Los logs locales de las verificaciones Maven se actualizaron en
`s10-docker-clean-test.log` y `s10-docker-clean-verify.log`, ignorados por Git;
Maven regeneró `target/`, también ignorado.

No se ejecutan suites opt-in sobre bases reales ni se repiten los smoke tests.
No se modifica código productivo, Dockerfile, Compose ni configuración.
No se ejecutan commit, push, merge, rebase, reset ni borrado de datos Docker.

## Capturas pendientes de guardar en el repositorio

En `docs/docker` no hay capturas físicamente guardadas al revisar el cierre.
No se asignan nombres ni rutas ficticias. Únicos pendientes documentales:

- [ ] Captura de build y arranque aprobados, y de `docker compose ps`.
- [ ] Captura de Swagger servido por la API Docker.
- [ ] Captura de login GERENTE y endpoint protegido, ocultando credenciales y token.
- [ ] Captura del historial PostgreSQL del pedido 1 y resultados `BeforeRestart` / `AfterRestart`.
- [ ] Captura de los tres documentos Mongo y del conteo, ocultando la URI autenticada.
- [ ] Captura de Docker Hub con imagen pública y tags `1.0.0` y `latest`.

Estas capturas quedan pendientes de incorporación, no de validación funcional.
No se documentan passwords, tokens, claves de firma, hashes, URI Mongo real
ni contenido de la configuración privada.

La arquitectura y el reporte de cierre están en
[implementation-report.md](implementation-report.md).
