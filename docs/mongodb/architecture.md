# S09.2 — MongoDB para auditoría/eventos

PostgreSQL conserva la fuente de verdad: Pedido y su historial obligatorio RN-07,
además de cuentas, pagos, usuarios, roles, mesas y el resto del negocio. MongoDB
es almacenamiento secundario de auditoría; no participa en decisiones de negocio.

```text
PostgreSQL
  └── Pedido + CambioEstadoPedidoEntity
       fuente de verdad (RN-07)

PedidoServiceImpl.cambiarEstado — @Transactional
  ├── valida transición y usuario
  ├── actualiza Pedido + historial JPA
  └── ApplicationEventPublisher.publishEvent(CambioEstadoPedidoAuditEvent)
       ↓ evento registrado en la transacción
       ↓ COMMIT PostgreSQL

@TransactionalEventListener(AFTER_COMMIT)
  └── EventoAuditoriaMongoListener
       ↓ EventoRestaurante (dominio)
       ↓ EventoRestauranteMapper.toDocument (MapStruct)
       ↓ EventoRestauranteDocument
       ↓ EventoRestauranteRepository.save (MongoRepository)

MongoDB
  └── eventos_restaurante
       auditoría secundaria
```

El evento se publica antes de que el proxy transaccional complete el commit,
justo después de `guardar(pedido)` y antes de retornar el pedido actualizado.
Esto registra la escucha en la transacción, sin escribir Mongo dentro de JPA.
El listener solo corre después del commit exitoso. Si la validación, la escritura
JPA o el commit fallan, no hay documento Mongo. `fallbackExecution` conserva el
valor predeterminado `false`: sin transacción no se guarda auditoría.

No se usa una transacción distribuida porque los repositorios JPA y Mongo no
comparten un gestor ACID en este proyecto. Una anotación `@Transactional` JPA
no vuelve atómica una escritura en las dos bases. Se mantiene RN-07 en
`CambioEstadoPedidoEntity`; Mongo observa únicamente cambios ya confirmados.

## Capas y contrato

| Componente | Responsabilidad |
| --- | --- |
| `model/domain/EventoRestaurante` | Datos conceptuales; sin anotaciones Spring/Mongo. |
| `model/document/EventoRestauranteDocument` | Persistencia Mongo sin lógica de negocio. |
| `mapper/EventoRestauranteMapper` | Domain → Document ignora ID; Document → Domain recupera ID. |
| `repository/EventoRestauranteRepository` | MongoRepository; consulta derivada ordenada por timestamp. |
| `service/auditoria/CambioEstadoPedidoAuditEvent` | Record interno inmutable; no transporta DTO ni entidades JPA. |
| `service/auditoria/EventoAuditoriaMongoListener` | Construye y persiste auditoría después del commit. |
| `service/EventoAuditoriaService` y su implementación | Lectura interna del dominio, sin endpoint HTTP ni transacción JPA. |

Colección: `eventos_restaurante`. Campos: `id` generado por Mongo,
`tipo=CAMBIO_ESTADO_PEDIDO`, `entidadTipo=Pedido`, `entidadId`, `descripcion`,
`usuario`, `timestamp` y `metadatos` con `estadoAnterior`/`estadoNuevo` como strings.
El timestamp proviene de `CambioEstadoPedidoEntity.fechaHora`, sin un segundo
reloj en el listener. BSON almacena fechas a precisión de milisegundos; PostgreSQL
puede conservar microsegundos. `LocalDateTime` usa las conversiones predeterminadas
Spring Data y la zona del proceso; utilizar una zona consistente entre instancias.

El tipo actual registra únicamente las transiciones RN-06:

- `RECIBIDO → EN_PREPARACION` (requiere pedido confirmado).
- `EN_PREPARACION → LISTO`.
- `LISTO → ENTREGADO`.

No se guarda información de autenticación: passwords, BCrypt, tokens JWT,
JWT secret o credenciales de conexión. El usuario es el responsable que ya
registra RN-07. JWT, roles, HTTPS y SecurityConfig permanecen intactos.

Consulta:

```java
eventoAuditoriaService.listarPorEntidad("Pedido", pedidoId);
```

Delega en `findByEntidadTipoAndEntidadIdOrderByTimestampAsc(String, Long)` y
transforma documentos a dominio conservando el orden. No se añade un endpoint:
el inventario continúa en 34. No se necesita MongoTemplate manual ni
`@EnableMongoRepositories` en producción.

## Fallos y garantías

**best-effort audit after commit**: el listener captura RuntimeException durante
la construcción/mapeo/persistencia y registra ERROR con tipo y pedidoId. Omite
mensaje y stack trace porque un error del driver puede incluir datos de conexión.
No intenta revertir PostgreSQL ni propaga una excepción Mongo al cliente.

El listener es síncrono después del commit: no mantiene atomicidad entre bases,
pero puede aumentar la latencia por el timeout del driver. Una caída del proceso
entre commit y escucha, o un Mongo no disponible, puede perder el evento. No se
garantiza entrega duradera ni hay reintentos de aplicación/deduplicación. Un sistema productivo
más robusto usaría un outbox transaccional y/o message broker. No se implementan
outbox, Kafka, RabbitMQ ni transacciones Mongo aquí.

La lectura interna sí informa al llamador los errores de consulta; no presenta
una lista vacía como si no existieran eventos cuando Mongo falla.

## Configuración y arranque

Dependencia única nueva: `spring-boot-starter-data-mongodb`, síncrona y con
versiones administradas por Spring Boot. Sin reactive, embedded Mongo ni
Testcontainers. No se crea `persistence/` en producción ni configuración de índices.

```properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/american_bites}
```

La futura configuración podrá usar
`MONGODB_URI=mongodb://mongo:27017/american_bites`. No hay Dockerfile ni compose.
La URI no contiene credenciales versionadas. El cliente/repositorio se inicializa
sin exigir una operación exitosa en Mongo durante startup. El monitor de fondo
del driver puede registrar un fallo de conexión; las operaciones posteriores
sí necesitan disponibilidad. No se añaden hacks ni creación de índices al inicio.

## Verificación y pendientes reales

Las pruebas normales verifican MapStruct en ambos sentidos, publicación exacta
para las tres transiciones válidas, ausencia de publicación en las inválidas,
campos y metadatos, consulta ordenada y arranque de la infraestructura Mongo con
un puerto inaccesible. El listener se registra en un contexto Spring con
`TransactionalEventListenerFactory` y un gestor de transacciones de prueba sin
BD: se comprueban AFTER_COMMIT, rollback explícito, excepción de negocio, fallo
de commit, publicación sin transacción y fallo Mongo que conserva la respuesta.
Esto prueba la sincronización; no equivale a una transacción PostgreSQL real.

Las 9 pruebas `S09RegressionPostgresIT` existentes no se modifican.

Prueba Mongo real opt-in:

```powershell
$env:MONGODB_URI = 'mongodb://localhost:27017/american_bites'
mvn '-Dtest=S09MongoAuditIT' '-Ds09.mongo=true' test
```

`S09MongoAuditIT` comprueba ping, inserción por mapper/repository, ID generado,
recuperación, todos los campos, exclusión de otra entidad con el mismo ID,
orden por timestamp y lectura por el service. Borra exclusivamente los IDs
creados por esa ejecución en `finally`, incluso si falla una aserción.
Sin `s09.mongo=true` se omite antes de cargar el contexto y no se descubre en
`mvn clean test`/`verify` por el patrón `IT`. Si se habilita sin Mongo disponible,
debe fallar: no se presenta como validación exitosa ni se sustituye por un mock.

En esta sesión no hay MONGODB_URI ni servidor local en localhost:27017.
También faltan DB_PASSWORD/JWT_SECRET en el proceso. **Mongo real, end-to-end
con ambas bases y evidencia visual permanecen pendientes.** Se conserva como
procedimiento manual para evitar agregar fixtures sobre dos bases antes de Docker.

Prueba manual end-to-end sobre bases dedicadas:

1. Configurar PostgreSQL, DB_PASSWORD, JWT_SECRET y MONGODB_URI; arrancar la API.
2. Crear mesa/cuenta/plato y pedido con un ítem; confirmar el pedido.
3. Autenticarse con el rol autorizado y pasar a EN_PREPARACION; verificar HTTP 200.
4. Leer el pedido y el historial RN-07 después de retornar la operación y verificar
   en PostgreSQL el estado y una fila de `cambios_estado_pedido` para ese pedido.
5. Consultar en Mongo `eventos_restaurante` por `entidadTipo: "Pedido"` y
   `entidadId` del pedido; comprobar tipo, estados, usuario y fecha a milisegundos.
6. Pasar a LISTO y ENTREGADO; verificar tres filas de historial y tres documentos
   ordenados por timestamp. Un intento inválido no agrega historial ni evento.
7. Detener Mongo, ejecutar otra transición válida con otro pedido y comprobar que
   la respuesta conserva éxito y RN-07 queda confirmado; verificar el ERROR seguro.
   Reiniciar Mongo: el evento fallido no se recupera automáticamente.
8. Para rollback real, usar una transacción envolvente desde una prueba/runner
   que invoque el service y marque rollback; comprobar ausencia de cambios en
   PostgreSQL y de documentos Mongo. No usar un endpoint nuevo para provocar esto.
9. Recopilar capturas reales sin secretos. Limpiar solo datos propios de pruebas.

No se afirma una validación real de estas bases mediante los tests con mocks.
