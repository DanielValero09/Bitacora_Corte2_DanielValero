# S09.1: corrección de concurrencia

Corrección aplicada en producción. La validación REAL en PostgreSQL permanece pendiente: DB_PASSWORD no está definida en el entorno de este proceso, ni en las variables de usuario o máquina. No se modificó ningún test existente, comprobado mediante hashes SHA-256 tomados antes y después. No se hizo commit ni push. No se avanzó a S09.2.

1. **Causa raíz de la doble confirmación.** `obtenerPedidoConCuentaBloqueada()` primero ejecutaba `buscarPedido()`, cargando `confirmado=false` antes de esperar el lock de Cuenta. Después adquiría el lock de Pedido mediante `findByIdForUpdate()`, pero Hibernate podía reutilizar la instancia administrada anterior. El lock de Pedido ya existía antes del cambio; faltaba releer su estado. La segunda transacción validaba el booleano antiguo y podía volver a establecer `fechaConfirmacion`.

2. **Causa raíz del estado stale.** Una transacción llamadora podía precargar Cuenta y Pedido. La consulta JPQL con `PESSIMISTIC_WRITE` bloqueaba la fila de Cuenta, pero no garantizaba reemplazar el estado de la instancia ya presente en el Persistence Context. La validación podía observar `ABIERTA` después de un cierre confirmado en otra conexión.

3. **Orden anterior.** Confirmación y las cuatro ediciones: lectura de Pedido sin lock para identificar Cuenta, lock Cuenta, lock Pedido, validaciones y mutaciones. Pago: lock Cuenta, comprobaciones, lectura del grafo para calcular el total, cierre y persistencia del Pago. Crear Pedido: lock Cuenta. Cambiar estado: lock Pedido. Abrir Cuenta: lock Mesa y verificación de cuenta abierta.

4. **Orden posterior.** Se conserva `Cuenta → Pedido`. La referencia inicial de Pedido solo identifica la Cuenta y no decide reglas de negocio. Tras el lock de Cuenta se refresca Cuenta; tras el lock de Pedido se refresca Pedido. Pago bloquea y refresca Cuenta y luego refresca/bloquea sus pedidos por ID ascendente antes de calcular el total. Cuenta coordina tanto edición como pago, por eso se adquiere primero.

5. **PedidoServiceImpl.** `buscarCuentaParaActualizar()` y `buscarPedidoParaActualizar()` llaman a `refreshForUpdate()` después de las consultas locking. Esto cubre crear Pedido, agregar ítem, actualizar cantidad, eliminar ítem, retirar bebida, confirmar y el estado actual usado por cambiarEstado. `confirmar()` conserva sus comprobaciones de RECIBIDO, no confirmado, ítems no vacíos, Cuenta ABIERTA y platos disponibles. Sus setters siguen ejecutándose después de todas las validaciones.

6. **PagoServiceImpl.** Refresca Cuenta antes de comprobar ABIERTA y antes de consultar el pago existente. Refresca sus pedidos en orden ascendente antes del mapper y del cálculo. Cuenta no tiene cascade REFRESH hacia Pedido; releer solamente Cuenta no basta para actualizar pedidos previamente cargados. Conserva `@Transactional`, UNIQUE cuenta_id, detección de pago duplicado, guardado del cierre y Pago en la misma transacción y una única fecha compartida entre ambos.

7. **Repositories.** CuentaRepository y PedidoRepository incorporan el fragmento compartido `LockingRefreshRepository`, implementado por `LockingRefreshRepositoryImpl`. Conservan sin cambios las consultas y las anotaciones `@Lock(PESSIMISTIC_WRITE)` existentes. MesaRepository y PagoRepository no se modificaron. El fragmento evita cambiar los constructores de los servicios o los fixtures de tests. La composición de fragmentos es un mecanismo de [Spring Data JPA 3.5](https://docs.spring.io/spring-data/jpa/reference/3.5/repositories/custom-implementations.html).

8. **EntityManager.** El fragmento recibe el EntityManager transaccional mediante `@PersistenceContext` y ejecuta `flush()` seguido de `refresh(entity, PESSIMISTIC_WRITE)`. El flush conserva cambios pendientes de llamadas anteriores autorizadas en la misma transacción; refresh sustituye el estado administrado por el de la base bajo lock. Los servicios ya adquirieron el lock coordinador antes de llamar al fragmento. No se usa clear ni se cambian relaciones LAZY, cascade o mapeos. La semántica de refresh está documentada en la [API de Jakarta Persistence](https://jakarta.ee/specifications/platform/10/apidocs/jakarta/persistence/entitymanager).

9. **Prevención de confirmación doble.** La segunda transacción espera Cuenta y Pedido, relee Pedido bajo lock y encuentra `confirmado=true`. Lanza el BusinessRuleException existente antes de ejecutar setters; no vuelve a establecer fechaConfirmacion. Esta explicación corresponde al flujo corregido; falta ejecutar la regresión real en este entorno.

10. **Prevención de edición posterior al pago.** Si Pago obtiene primero Cuenta, edición espera y refresca esa misma instancia administrada al continuar. La validación observa CERRADA y rechaza la modificación. Si edición obtiene Cuenta primero, conserva el lock hasta terminar su transacción; Pago espera y después refresca Cuenta y sus pedidos antes de calcular el total. El refresh de Pedido conserva la actualización de sus ítems mediante el cascade REFRESH ya incluido en CascadeType.ALL.

11. **Deadlocks.** No se encontró un ciclo en los métodos auditados: las operaciones con ambas filas adquieren Cuenta antes que Pedido. Pago procesa varios pedidos por ID ascendente. CambiarEstado bloquea Pedido y accede al ID de Cuenta para mapear, pero no intenta bloquear Cuenta. AbrirCuenta bloquea Mesa; estos flujos de Pedido/Pago no adquieren un lock de Mesa posteriormente. No hay synchronized global ni sleeps. La comprobación bajo carga real sigue pendiente junto con la suite PostgreSQL.

12. **Pruebas PostgreSQL.** Primero se ejecutó la selección de las dos regresiones: Tests run: 2, Failures: 0, Errors: 0, Skipped: 2. Después se ejecutó exactamente `mvn '-Dtest=S09RegressionPostgresIT' '-Ds09.postgres=true' test`: Tests run: 9, Failures: 0, Errors: 0, Skipped: 9. Ambos comandos terminaron con BUILD SUCCESS por la condición preexistente que exige DB_PASSWORD. **No representan 9 pruebas PostgreSQL aprobadas.** Debe repetirse en el entorno que hereda DB_PASSWORD, sin cambiar los tests ni sus condiciones.

13. **Pruebas normales.** 224 tests, 0 failures, 0 errors, 0 skipped tanto en clean test como en clean verify. Los cambios de tests visibles en git status ya estaban presentes al comenzar; sus hashes no cambiaron durante esta intervención.

14. **JaCoCo.** En ambas ejecuciones limpias: instrucciones 88,80% (3355/3778); líneas 85,46% (717/839); ramas 65,05% (121/186); métodos 94,06% (206/219); complejidad 79,49% (248/312); clases 95,24% (60/63). PedidoServiceImpl y PagoServiceImpl: 100% de líneas y ramas. El nuevo LockingRefreshRepositoryImpl tiene 0% en la suite normal, porque esta usa repositories simulados; no se afirma cobertura ni validación real del fragmento contra PostgreSQL. Reporte: `target/site/jacoco/index.html`.

15. **mvn clean test.** BUILD SUCCESS; 224/224, sin fallos, errores ni omitidas.

16. **mvn clean verify.** BUILD SUCCESS; 224/224, sin fallos, errores ni omitidas; JAR generado y repaquetado correctamente.

17. **git diff --check.** Exit code 0, sin errores de whitespace. Git emite avisos por la conversión LF/CRLF configurada en el repositorio.

18. **Archivos modificados por esta intervención.** Cuatro archivos existentes: `src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java`, `src/main/java/com/restaurante/service/impl/PagoServiceImpl.java`, `src/main/java/com/restaurante/repository/CuentaRepository.java`, `src/main/java/com/restaurante/repository/PedidoRepository.java`. Tres archivos nuevos: `src/main/java/com/restaurante/repository/LockingRefreshRepository.java`, `src/main/java/com/restaurante/repository/LockingRefreshRepositoryImpl.java` y este reporte. No se modificaron los demás archivos presentes inicialmente en src, docs o scripts. Logs completos de los cuatro comandos Maven: `target/concurrency-audit/`.

19. **git status --short.** Estado completo del workspace, incluyendo el trabajo previo:

```text
 M docs/diagrams/order-flow-sequence.puml
 M src/main/java/com/restaurante/service/impl/CuentaServiceImpl.java
 M src/main/java/com/restaurante/service/impl/MesaServiceImpl.java
 M src/main/java/com/restaurante/service/impl/PagoServiceImpl.java
 M src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java
 M src/test/java/com/restaurante/RestauranteApplicationTests.java
 M src/test/java/com/restaurante/controller/MesaCuentaHttpTest.java
 M src/test/java/com/restaurante/controller/PagoHttpTest.java
 M src/test/java/com/restaurante/controller/PedidoHttpTest.java
 M src/test/java/com/restaurante/integration/AmericanBitesHttpIntegrationTest.java
 M src/test/java/com/restaurante/integration/AmericanBitesIntegrationTest.java
 M src/test/java/com/restaurante/service/impl/CuentaServiceImplTest.java
 M src/test/java/com/restaurante/service/impl/MesaServiceImplTest.java
 M src/test/java/com/restaurante/service/impl/PagoIntegrationTest.java
 M src/test/java/com/restaurante/service/impl/PagoServiceImplTest.java
 M src/test/java/com/restaurante/service/impl/PedidoServiceImplTest.java
?? docs/audit/
?? docs/s09-1-concurrency-fix-report.md
?? docs/s09-1-regression-audit.md
?? scripts/
?? src/main/java/com/restaurante/mapper/CambioEstadoPedidoEntityMapper.java
?? src/main/java/com/restaurante/mapper/CuentaEntityMapper.java
?? src/main/java/com/restaurante/mapper/ItemPedidoEntityMapper.java
?? src/main/java/com/restaurante/mapper/MesaEntityMapper.java
?? src/main/java/com/restaurante/mapper/PagoEntityMapper.java
?? src/main/java/com/restaurante/mapper/PedidoEntityMapper.java
?? src/main/java/com/restaurante/model/entity/CambioEstadoPedidoEntity.java
?? src/main/java/com/restaurante/model/entity/CuentaEntity.java
?? src/main/java/com/restaurante/model/entity/ItemPedidoEntity.java
?? src/main/java/com/restaurante/model/entity/MesaEntity.java
?? src/main/java/com/restaurante/model/entity/PagoEntity.java
?? src/main/java/com/restaurante/model/entity/PedidoEntity.java
?? src/main/java/com/restaurante/repository/CuentaRepository.java
?? src/main/java/com/restaurante/repository/LockingRefreshRepository.java
?? src/main/java/com/restaurante/repository/LockingRefreshRepositoryImpl.java
?? src/main/java/com/restaurante/repository/MesaRepository.java
?? src/main/java/com/restaurante/repository/PagoRepository.java
?? src/main/java/com/restaurante/repository/PedidoRepository.java
?? src/test/java/com/restaurante/persistence/
?? src/test/java/com/restaurante/support/RelationalTestFixture.java
```

Detenido en S09.1. Pendiente exclusivamente de validación real en PostgreSQL para afirmar que las nueve regresiones pasan.
