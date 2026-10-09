# Reporte final S09.2 — auditoría MongoDB

Fecha: 06/10/2026. Implementación y verificación local completadas. Mongo real,
end-to-end sobre ambas bases y evidencia visual quedan pendientes. No se realizó
commit, push, merge, rebase ni reset. No se implementó Docker ni CI/CD.

1. **Dependencia.** Agregada `spring-boot-starter-data-mongodb`, síncrona, sin
   versión manual. `dependency:tree` terminó con BUILD SUCCESS: Spring Boot
   3.5.16, Spring Data MongoDB 4.5.13 y driver sync 5.5.2, sin conflicto detectado.
   Sin reactive, embedded Mongo, Testcontainers ni librerías adicionales.

2. **Configuración.** En main y test:
   `spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/american_bites}`.
   Sin credenciales reales. Compatible con la futura URI
   `mongodb://mongo:27017/american_bites`.

3. **Dominio.** `model/domain/EventoRestaurante.java`, con los ocho campos
   solicitados y Lombok consistente con el proyecto. No depende de Mongo/Spring Data.

4. **Documento.** `model/document/EventoRestauranteDocument.java`, con `@Document`
   y `@Id String id`. Solo datos de persistencia, sin reglas de negocio.

5. **Colección.** `eventos_restaurante`. El ID de creación se deja nulo para
   que Mongo lo genere. No contiene passwords, hashes, JWT ni secrets.

6. **Repositorio.** `EventoRestauranteRepository extends
   MongoRepository<EventoRestauranteDocument, String>`, bajo `repository/`.
   No se creó `persistence/` en producción ni se usa MongoTemplate manual.

7. **Query derivada.**
   `findByEntidadTipoAndEntidadIdOrderByTimestampAsc(String, Long)`.
   Se implementa la consulta requerida por entidad; no se añade la consulta
   opcional por tipo/rango porque no hay un caso de uso actual que la necesite.

8. **Mapper.** `EventoRestauranteMapper` MapStruct, `componentModel="spring"`
   y `unmappedTargetPolicy=ERROR`, siguiendo el criterio EntityMapper.
   Domain → Document ignora ID; Document → Domain conserva ID.

9. **Evento interno.** Record inmutable `CambioEstadoPedidoAuditEvent` con
   pedidoId, estadoAnterior, estadoNuevo, usuarioResponsable y fechaHora.
   No transporta DTO HTTP ni entidades JPA.

10. **Punto de publicación.** `PedidoServiceImpl.cambiarEstado`, inmediatamente
    después de `Pedido actualizado = guardar(pedido)` y antes de retornar.
    Publica mediante `ApplicationEventPublisher` dentro de la transacción JPA.
    Usa la fecha y el usuario del mismo `CambioEstadoPedidoEntity`.

11. **Listener.** `EventoAuditoriaMongoListener`,
    `@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)`.
    Crea EventoRestaurante → mapper → document → repository.save.
    Conserva `fallbackExecution=false`: sin transacción no hay escritura.

12. **Fallo Mongo.** Captura RuntimeException y registra ERROR solo con
    `tipo=CAMBIO_ESTADO_PEDIDO` y pedidoId; omite mensaje/stack trace del driver.
    No revierte PostgreSQL ni propaga un fallo Mongo después del commit.
    Estrategia **best-effort audit after commit**: puede perder eventos y
    aumentar latencia por el timeout. Sin reintentos de aplicación ni entrega
    duradera. Outbox/message broker se documentan como mejora futura.

13. **RN-07 intacta.** Se conserva la actualización de Pedido y la adición de
    CambioEstadoPedidoEntity al historial PostgreSQL. No cambia su entity,
    asociación, mapper ni reglas RN-01 a RN-07. Las nueve pruebas
    `S09RegressionPostgresIT` permanecen sin modificaciones; también se conserva
    `S10UsuarioPostgresIT`. No se reejecutó PostgreSQL real en esta sesión.

14. **Seguridad intacta.** Sin cambios en SecurityConfig, JWT, usuarios, roles
    ni HTTPS de producción. `SecurityHttpTest` solo añade un repositorio Mongo
    mock y excluye la autoconfiguración Mongo para evitar una conexión en tests.
    Sus 86 invocaciones siguen pasando. No se añade endpoint; permanecen 34.

15. **Pruebas agregadas.** 18 invocaciones normales:
    2 del mapper, 9 del listener transaccional, 2 del service de lectura,
    1 de arranque sin servidor y 4 de publicación en PedidoServiceImplTest.
    Las nueve transiciones inválidas preexistentes ahora también verifican
    ausencia de publicación. Las tres válidas publican exactamente un evento.
    Se comprueban campos, metadatos, usuario, timestamp, orden, AFTER_COMMIT,
    rollback, excepción principal, fallo de commit, ausencia de transacción
    y Mongo fallido sin romper el retorno ni exponer el mensaje sensible.
    No se elimina ningún método de prueba anterior.

16. **Total.** `mvn clean test` y `mvn clean verify`: **344 pruebas**,
    **0 fallos**, **0 errores**, **0 omitidas**. Se conservan las 326 anteriores
    y se suman 18. Las suites opt-in no se cuentan en este total.

17. **JaCoCo.** Resultado del `clean verify`:

    | Métrica | Cubierto / total | Cobertura |
    | --- | --- | --- |
    | Instrucciones | 4366 / 4793 | 91,09 % |
    | Líneas | 937 / 1061 | 88,31 % |
    | Ramas | 163 / 234 | 69,66 % |
    | Métodos | 266 / 279 | 95,34 % |

    Listener y service de auditoría: 100 % de líneas. PedidoServiceImpl:
    100 % de líneas y ramas. Reporte generado: `target/site/jacoco/index.html`.

18. **S09MongoAuditIT.** Creada bajo `src/test/java/com/restaurante/auditoria/`.
    Habilitar con `mvn '-Dtest=S09MongoAuditIT' '-Ds09.mongo=true' test`.
    Usa MONGODB_URI, comprueba ping, inserción, ID, recuperación, campos, filtro
    por Pedido, orden y lectura por service. Borra solo documentos propios por ID
    en `finally`; nunca toda la colección. La ejecución explícita sin la propiedad
    produjo **1 omitida**, 0 errores, BUILD SUCCESS, sin cargar contexto Mongo.

19. **Mongo real.** **No ejecutado ni validado**: no está definida MONGODB_URI y
    no responde localhost:27017. El test de arranque con puerto inaccesible
    confirma inicialización de la infraestructura, no persistencia real.

20. **End-to-end.** No se añade una suite sobre ambas bases. Procedimiento manual
    pendiente en architecture.md, incluida comprobación RN-07, tres eventos,
    transición inválida, Mongo caído y rollback. También faltan DB_PASSWORD y
    JWT_SECRET en el proceso; no se fabrican resultados de integración.

21. **README.** Añadida sección “Persistencia NoSQL — MongoDB”: responsabilidades,
    colección, URI sin secretos, evento, AFTER_COMMIT, pérdidas/latencia,
    prueba opt-in y evidencia visual pendiente. Actualizadas tecnología y capas.

22. **Arquitectura.** Creado `docs/mongodb/architecture.md`: flujo PostgreSQL →
    publicación → commit → listener → Mongo, ausencia de transacción distribuida,
    contrato, errores, lectura, configuración y procedimiento manual.

23. **Archivos creados (15).**

    ```text
    docs/mongodb/architecture.md
    docs/mongodb/implementation-report.md
    src/main/java/com/restaurante/model/domain/EventoRestaurante.java
    src/main/java/com/restaurante/model/document/EventoRestauranteDocument.java
    src/main/java/com/restaurante/repository/EventoRestauranteRepository.java
    src/main/java/com/restaurante/mapper/EventoRestauranteMapper.java
    src/main/java/com/restaurante/service/EventoAuditoriaService.java
    src/main/java/com/restaurante/service/impl/EventoAuditoriaServiceImpl.java
    src/main/java/com/restaurante/service/auditoria/CambioEstadoPedidoAuditEvent.java
    src/main/java/com/restaurante/service/auditoria/EventoAuditoriaMongoListener.java
    src/test/java/com/restaurante/auditoria/MongoAuditStartupTest.java
    src/test/java/com/restaurante/auditoria/S09MongoAuditIT.java
    src/test/java/com/restaurante/mapper/EventoRestauranteMapperTest.java
    src/test/java/com/restaurante/service/auditoria/EventoAuditoriaMongoListenerTest.java
    src/test/java/com/restaurante/service/impl/EventoAuditoriaServiceImplTest.java
    ```

24. **Archivos modificados (9).**

    ```text
    README.md
    pom.xml
    src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java
    src/main/resources/application.properties
    src/test/java/com/restaurante/RestauranteApplicationTests.java
    src/test/java/com/restaurante/security/SecurityHttpTest.java
    src/test/java/com/restaurante/service/impl/PedidoServiceImplTest.java
    src/test/java/com/restaurante/support/RelationalTestFixture.java
    src/test/resources/application.properties
    ```

25. **mvn clean test.** BUILD SUCCESS: 344, 0 fallos/errores/omisiones.
    Log local ignorado por Git: `s09-mongo-test.log`.

26. **mvn clean verify.** BUILD SUCCESS, salida Maven 0: 344,
    0 fallos/errores/omisiones. JAR ejecutable generado en
    `target/restaurante-0.0.1-SNAPSHOT.jar`. Log: `s09-mongo-verify.log`.

27. **git diff --check.** Aprobado, salida 0, sin errores de whitespace.
    Los avisos de conversión LF → CRLF corresponden a la configuración Git
    existente en Windows y no son errores de diff.

28. **git status --short.** Working tree con nueve archivos modificados y
    quince nuevos, sin cambios staged. Estado:

    ```text
     M README.md
     M pom.xml
     M src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java
     M src/main/resources/application.properties
     M src/test/java/com/restaurante/RestauranteApplicationTests.java
     M src/test/java/com/restaurante/security/SecurityHttpTest.java
     M src/test/java/com/restaurante/service/impl/PedidoServiceImplTest.java
     M src/test/java/com/restaurante/support/RelationalTestFixture.java
     M src/test/resources/application.properties
    ?? docs/mongodb/
    ?? src/main/java/com/restaurante/mapper/EventoRestauranteMapper.java
    ?? src/main/java/com/restaurante/model/document/
    ?? src/main/java/com/restaurante/model/domain/EventoRestaurante.java
    ?? src/main/java/com/restaurante/repository/EventoRestauranteRepository.java
    ?? src/main/java/com/restaurante/service/EventoAuditoriaService.java
    ?? src/main/java/com/restaurante/service/auditoria/
    ?? src/main/java/com/restaurante/service/impl/EventoAuditoriaServiceImpl.java
    ?? src/test/java/com/restaurante/auditoria/
    ?? src/test/java/com/restaurante/mapper/EventoRestauranteMapperTest.java
    ?? src/test/java/com/restaurante/service/auditoria/
    ?? src/test/java/com/restaurante/service/impl/EventoAuditoriaServiceImplTest.java
    ```

29. **Pendientes manuales.** Ejecutar S09MongoAuditIT con Mongo real; ejecutar
    el procedimiento end-to-end con PostgreSQL + Mongo; obtener capturas reales
    de documentos y campos sin secretos. No se avanza a Docker ni CI/CD.

Implementación detenida en S09.2 al entregar este reporte.
