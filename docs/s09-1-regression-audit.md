# Auditoría de regresión S09.1

Fecha: 01/10/2026. Baseline Git: `aa391a1` (`HEAD`); S09.1 continúa en el working tree. No se modificó producción durante esta auditoría, ni se implementó MongoDB/JWT, ni se hizo commit/push.

**Dictamen: no cerrar todavía S09.1.** Las pruebas locales están correctas, pero no equivalen a validar concurrencia/persistencia en PostgreSQL. Falta ejecutar la suite real con credenciales. Se identificaron fragilidad de `ddl-auto=update` y riesgo de estado precargado antes del lock en Pedido; no se reemplazó la columna generada ni se alteró el locking.

## 1. Explicación exacta de 221 → 171

Antes de limpiar `target` se conservaron los 25 XML de Surefire en [s09-1-surefire-before-audit.json](audit/s09-1-surefire-before-audit.json): suman exactamente 171 ejecuciones, todas sin errores/omisiones, fechadas el 29/09. Los fuentes de cuatro suites habían recibido ampliaciones el 01/10 y eran posteriores a esos XML. La primera ejecución limpia de esta auditoría, antes de agregar pruebas, ya dio 221. No se atribuye esa restauración previa a esta auditoría.

| Suite | HEAD antes de S09.1 | XML de 171 | Reducción | Fuentes al iniciar auditoría | Final local |
| --- | ---: | ---: | ---: | ---: | ---: |
| CuentaServiceImplTest | 12 | 9 | 3 | 12 | 12 |
| MesaServiceImplTest | 7 | 6 | 1 | 7 | 7 |
| PagoServiceImplTest | 14 | 6 | 8 | 14 | 14 |
| PedidoServiceImplTest | 53 | 15 | 38 | 53 | 55 |
| Otras 21 suites originales | 135 | 135 | 0 | 135 | 135 |
| PostgreSqlSchemaTest, nueva | 0 | 0 | 0 | 0 | 1 |
| **Total** | **221** | **171** | **50** | **221** | **224** |

Por nombres de método, 74 firmas @Test anteriores y una @ParameterizedTest (9 invocaciones) no aparecen en el XML de 171; aparecen 33 @Test con nombres nuevos en esas cinco suites reescritas. El balance es **221 - 74 - 9 + 33 = 171**. Los 33 nombres nuevos se distribuyen en Cuenta 6, Mesa 3, Pago 6, Pedido 14 y PagoIntegration 4. Parte son renombrados/agrupaciones, no necesariamente reglas desaparecidas; el inventario enumera las 75 firmas y su evidencia equivalente.

La reducción neta resulta de estas reescrituras, no de exclusiones Maven:

| Suite | @Test anteriores | @Test en 171 | Parametrizadas anteriores → 171 | Balance exacto |
| --- | ---: | ---: | --- | --- |
| Cuenta | 12 | 9 | ninguna | -3 |
| Mesa | 7 | 6 | ninguna | -1 |
| Pago | 14 | 6 | ninguna | -8 |
| Pedido | 44 | 15 | 1 método / 9 casos → 0 | -29 -9 = -38 |

Ejemplos concretos del cambio de evidencia:

- Cuenta: desaparecieron la invocación de mesa inexistente al consultar cuenta abierta, la lista ordenada y la apertura concurrente. Los primeros dos casos están nuevamente en los fuentes actuales. La apertura concurrente se preparó para PostgreSQL real.
- Mesa: desapareció `idsSonDiferentesEIncrementales`. Se mantiene generación/unicidad, pero PostgreSQL IDENTITY no promete IDs consecutivos sin saltos. Se verificó generación del DDL y quedó preparado el flujo con IDs reales. El test de captura de violación UNIQUE es evidencia de traducción de excepción, no prueba de dos conexiones.
- Pago: 14 casos separados se compactaron en 6: consultas por ID/cuenta, varios NotFound y lista vacía se agruparon; la concurrencia de dos pagos y la lista ordenada perdieron ejecución propia. Hoy hay 14, pero la cifra coincidente no garantiza la misma evidencia. La concurrencia se restituyó en la suite PostgreSQL.
- Pedido: 44 tests simples se compactaron en 15. Se agruparon crear/consultar/listar errores, agregar/actualizar errores, actualizar/eliminar/retirar bebida y validaciones de transición. Desapareció íntegramente `transicionesInvalidasNoModificanEstadoNiHistorial`: nueve argumentos originales, ahora restaurados. También desaparecieron concurrencia de confirmación/transición/agregado y la regla de edición del confirmado antes de preparación; esta última se recuperó aquí.
- `PagoIntegrationTest` permaneció en 4, pero reemplazó el caso pago contra agregado concurrente y parte del flujo de totales por dos inspecciones de anotaciones (`seccionesCriticasDeclaranBloqueoPesimista`, `restriccionesDeBaseProtegenPagoYCuentaAbierta`). No produce reducción numérica, pero sí pérdida de evidencia operacional.

**Archivos de test eliminados: ninguno. Tests deshabilitados en la migración: ninguno. Suites originales fuera del descubrimiento Surefire: ninguna.** `pom.xml` no cambió contra HEAD y no introduce excludes de Surefire; el exclude existente es de Lombok en el empaquetado Spring Boot. Las parametrizadas HTTP y GlobalExceptionHandler conservaron sus fuentes de datos. No hay otra causa numérica: 3 + 1 + 8 + 29 + 9 = 50.

El [inventario de métodos](audit/s09-1-test-method-inventory.json) contiene todos los nombres anteriores/actuales. La [trazabilidad de comportamiento](audit/s09-1-test-behavior.md) enumera los métodos que desaparecen o se reescriben, sus equivalentes y las invocaciones ausentes del reporte de 171. Como S09.1 no tiene commit, Git no conserva los fuentes exactos intermedios de 171: se usaron sus XML para el conteo, y el diff actual para revisar reglas y aserciones.

## 2. Restauración/adaptación realizada en esta auditoría

Se agregaron dos pruebas de reglas válidas en `PedidoServiceImplTest`:

- `pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion`: admite cambiar cantidad después de confirmar; después de EN_PREPARACION rechaza el cambio y conserva cantidad.
- `platoDesactivadoDespuesDeAgregarseImpideConfirmarSinCambiosParciales`: conserva el caso anterior de plato inactivo. El caso existente `productoAgotadoDespuesDeAgregarseImpideConfirmar` ahora valida ingrediente agotado; eran dos reglas de disponibilidad distintas.

Se reforzaron snapshot repetido (nombre/precio/combo/bebida/un solo ítem), idempotencia de retirada de bebida (incluida cantidad), RN-07 (campos de los tres registros), tablero (RECIBIDO/EN_PREPARACION/LISTO incluidos y no confirmados/ENTREGADO excluidos), inicialización de Cuenta y solicitud explícita de orden al Repository. Se adaptó la evidencia a Repository/EntityMapper y lecturas de dominio reconstruido. No se restauraron contratos de referencia Java compartida ni consecutividad exacta del contador en memoria.

Se agregó `PostgreSqlSchemaTest`: utiliza el Hibernate 6.6.53.Final realmente resuelto por el proyecto para generar DDL PostgreSQL sin conexión. Verifica columna generada, UNIQUE de cuenta abierta, UNIQUE de pago, IDENTITY y FKs, incluyendo ausencia de FK Item→Plato. El SQL completo se genera en `target/s09-1-postgresql-create.sql` y se conserva como [evidencia](audit/s09-1-postgresql-create.sql).

Se preparó `S09RegressionPostgresIT`, con nueve tests y cero mocks: seis escenarios de concurrencia anteriores, flujo completo con reinicio, regresión determinista de contexto precargado y restricciones/orphanRemoval/rollback reales. El sufijo `PostgresIT` es deliberadamente manual: Surefire no lo descubre por defecto, no hay Failsafe configurado, y exige además `-Ds09.postgres=true` y `DB_PASSWORD`. Estos nueve tests **no están contados como aprobados, ejecutados ni omitidos** en las 224 pruebas locales. Se compiló su código, sin iniciar la aplicación con PostgreSQL.

## 3. Cobertura individual y global

JaCoCo sin exclusions adicionales, después de una compilación limpia:

| ServiceImpl | Instrucciones | Líneas | Ramas |
| --- | --- | --- | --- |
| MesaServiceImpl | 104/104 = 100% | 19/19 = 100% | **2/2 = 100%** |
| CuentaServiceImpl | 160/160 = 100% | 32/32 = 100% | **4/4 = 100%** |
| PedidoServiceImpl | 610/610 = 100% | 143/143 = 100% | **48/48 = 100%** |
| PagoServiceImpl | 188/188 = 100% | 40/40 = 100% | **6/6 = 100%** |

| Global | Cubierto / total | Porcentaje |
| --- | --- | ---: |
| Instrucciones | 3324 / 3735 | **89,00%** |
| Líneas | 707 / 825 | **85,70%** |
| Ramas | 121 / 186 | **65,05%** |

La medición comunicada anteriormente fue 88,09% / 84,61% / 61,83%. La cobertura de ramas de los cuatro servicios no tiene ramas de bytecode pendientes; se ejecutaron lados positivos/negativos de cuenta abierta, confirmación, disponibilidad, cantidad, combo, usuario y transiciones. Eso no mide aislamiento, rollback, restricciones SQL ni supervivencia a reinicios. Los huecos de negocio identificados eran aserciones/reglas recuperadas y concurrencia real, aunque todos los branches ya estuvieran verdes. No se agregaron tests de nulls generados por MapStruct ni de builders para inflar el global.

Reportes: `target/site/jacoco/index.html`, `jacoco.xml`, `jacoco.csv`; [métricas conservadas](audit/s09-1-final-metrics.json). El global incluye dominio, DTO/mappers y clases generadas; no se confunde con cobertura individual de servicios.

## 4. Entidades y relaciones

Las seis entidades tienen `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Sus enums persistentes tienen `@Enumerated(EnumType.STRING)`; el DDL genera además CHECK de valores de enum. Usan Getter/Setter/Builder/constructores, sin `@Data`, `@EqualsAndHashCode` ni `@ToString`: conservan igualdad/hash/toString de Object, sin recorrer asociaciones. No contienen cálculo de totales, validaciones ni transiciones de negocio.

| Entidad / relación | Carga y propiedad | Cascade / orphanRemoval | Restricciones y evaluación |
| --- | --- | --- | --- |
| MesaEntity | sin colecciones inversas | ninguno | número NOT NULL y `uk_mesas_numero`; ID PK |
| CuentaEntity→Mesa | ManyToOne LAZY, optional=false; FK mesa_id | ninguno | FK NOT NULL; muchas cuentas históricas por mesa |
| CuentaEntity→Pedidos | OneToMany LAZY, mappedBy=cuenta; `@OrderBy("id ASC")` | ninguno / false | correcto: Pedido se guarda por su Repository; no eliminar pedidos al retirar una referencia de Cuenta |
| PedidoEntity→Cuenta | ManyToOne LAZY, optional=false; FK cuenta_id | ninguno | FK NOT NULL; estado/confirmado/fechaCreacion NOT NULL; fechaConfirmacion opcional |
| PedidoEntity→Items | OneToMany LAZY, mappedBy=pedido; orden ID | ALL / **true** | apropiado para hijos del pedido editables/eliminables; quitar ítem debe borrar su fila |
| PedidoEntity→Historial | OneToMany LAZY, mappedBy=pedido; orden ID | ALL / **false** | persist/merge propagan RN-07; quitar referencia no borra historial. ALL también borraría historial al eliminar físicamente Pedido; no existe servicio de eliminación de Pedido |
| ItemPedidoEntity→Pedido | ManyToOne LAZY, optional=false | ninguno | FK NOT NULL; los seis campos snapshot son NOT NULL |
| PagoEntity→Cuenta | OneToOne LAZY, optional=false, lado propietario | ninguno | FK NOT NULL + UNIQUE real; monto numeric(12,2), fechaHora NOT NULL |
| CambioEstadoPedidoEntity→Pedido | ManyToOne LAZY, optional=false | ninguno | FK NOT NULL; estados, usuario ≤100 y fechaHora NOT NULL |

FechaCierre y fechaConfirmacion nulas son coherentes para abierta/no confirmado. No hay FK Item→Plato: platoId es un Long ordinario deliberadamente congelado. Las validaciones de cantidad/precio positivos están en DTO/services; el esquema actual no agrega CHECK numérico para impedir inserciones SQL externas negativas. Tampoco hay CHECK cruzado estado de Cuenta/fechaCierre; el servicio mantiene esa consistencia.

`CuentaEntityMapper` y `PedidoEntityMapper` reconstruyen IDs de padres sin recursión, mapean hijos y sus `@AfterMapping` enlazan de vuelta las entidades cuando se usa `toEntity`. El fixture usa mappers reales hacia dominio, pero sus mapas comparten entidades en memoria y no representan un PersistenceContext con snapshots de dos conexiones.

## 5. Máximo una Cuenta ABIERTA por Mesa

Columna: **cuentas.cuenta_abierta**, propiedad JPA `Boolean cuentaAbierta`:

```java
@Column(name = "cuenta_abierta", insertable = false, updatable = false,
        columnDefinition = "boolean generated always as "
                + "(case when estado = 'ABIERTA' then true else null end) stored")
private Boolean cuentaAbierta;
```

La tabla declara `@UniqueConstraint(name="uk_cuentas_mesa_abierta", columnNames={"mesa_id","cuenta_abierta"})`. Hibernate generó efectivamente:

```sql
create table cuentas (
    cuenta_abierta boolean generated always as
        (case when estado = 'ABIERTA' then true else null end) stored,
    fecha_apertura timestamp(6) not null,
    fecha_cierre timestamp(6),
    id bigint generated by default as identity,
    mesa_id bigint not null,
    estado varchar(20) not null check (estado in ('ABIERTA','CERRADA')),
    primary key (id),
    constraint uk_cuentas_mesa_abierta unique (mesa_id, cuenta_abierta)
);
```

PostgreSQL permite expresiones generadas almacenadas sobre columnas de la fila. ABIERTA produce `(mesa_id, TRUE)`, cuya duplicación está prohibida; CERRADA produce `(mesa_id, NULL)`. La UNIQUE ordinaria considera distintos los NULL y admite muchas cerradas. No se usa `NULLS NOT DISTINCT`. Referencias: [columnas generadas PostgreSQL](https://www.postgresql.org/docs/18/ddl-generated-columns.html), [UNIQUE y NULL](https://www.postgresql.org/docs/18/ddl-constraints.html#DDL-CONSTRAINTS-UNIQUE-CONSTRAINTS).

**Creación inicial: DDL válido, verificado con Hibernate real; ejecución en servidor pendiente.** No hay problema sintáctico encontrado en la columna generada. Es específica de PostgreSQL compatible con generated stored (desde PostgreSQL 12).

**Update: frágil como garantía de migración.** Si falta la tabla, Hibernate la crea. Si existe la tabla y falta la columna, el migrador agrega su definición, equivalente a:

```sql
alter table if exists cuentas add column cuenta_abierta boolean
    generated always as (case when estado = 'ABIERTA' then true else null end) stored;
alter table if exists cuentas add constraint uk_cuentas_mesa_abierta
    unique (mesa_id, cuenta_abierta);
```

Si ya existe una columna BOOLEAN ordinaria de igual nombre/tipo, `update` no comprueba/repara su expresión generada: podría dejar una columna que sigue en NULL y una UNIQUE ineficaz. Tampoco soluciona cuentas ABIERTAS duplicadas previas; agregar la restricción fallaría. Hibernate puede intentar drop/recreate de UNIQUE y tolerar errores de esas operaciones, por lo que un arranque sin excepción no certifica la restricción. Revisado en el [migrador de columnas 6.6.53](https://github.com/hibernate/hibernate-orm/blob/6.6.53/hibernate-core/src/main/java/org/hibernate/tool/schema/internal/StandardTableMigrator.java) y el [migrador de UNIQUE 6.6.53](https://github.com/hibernate/hibernate-orm/blob/6.6.53/hibernate-core/src/main/java/org/hibernate/tool/schema/internal/AbstractSchemaMigrator.java).

La propiedad generada es insertable/updatable=false, pero no declara `@Generated`; una entidad ya administrada no necesariamente recibe inmediatamente el Boolean recalculado. No afecta al negocio actual: no se usa el campo en decisiones/mappers. Se reporta como limitación; **no se sustituyó la implementación**.

## 6. Locks y transacciones

| Repository | Método con @Lock(PESSIMISTIC_WRITE) | Invocadores de producción |
| --- | --- | --- |
| MesaRepository | `findByIdForUpdate(Long)` | CuentaServiceImpl.abrirCuenta |
| CuentaRepository | `findByIdForUpdate(Long)` | PedidoServiceImpl.crear; agregarItem/actualizarCantidadItem/eliminarItem/retirarBebidaCombo/confirmar mediante helpers; PagoServiceImpl.registrarPago |
| PedidoRepository | `findByIdForUpdate(Long)` | PedidoServiceImpl.agregarItem/actualizarCantidadItem/eliminarItem/retirarBebidaCombo/confirmar mediante helpers; cambiarEstado directamente mediante helper |
| PagoRepository | **ninguno** | exclusión coordinada bloqueando Cuenta, más UNIQUE de pagos.cuenta_id |

Todos esos caminos públicos de producción tienen `@Transactional` de escritura. Los helpers privados se ejecutan dentro de esa transacción al entrar al servicio vía proxy Spring; no hay llamadas de producción por constructores directos ni self-invocation que deje el lock sin transacción. Los tests unitarios construyen services directamente: allí la anotación no inicia una transacción y sus repositorios simulados no bloquean. [Referencia Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html).

- **Dos aperturas:** bloquean la misma fila Mesa antes de exists ABIERTA; con el aislamiento habitual READ COMMITTED, el segundo espera y luego ve la cuenta creada. La UNIQUE es defensa adicional si fue instalada realmente. Prueba de dos conexiones pendiente.
- **Dos pagos:** bloquean Cuenta, comprueban ABIERTA y exists pago; la UNIQUE evita una segunda fila aun con un escritor externo. Sin estado precargado, el segundo debe rechazar cuenta cerrada. Prueba real pendiente; no afirmar que la inspección de `@Lock` la reemplaza.
- **Modificar Pedido contra pagar:** ambos bloquean Cuenta; Pedido después bloquea Pedido. La intención de serializar total/contenido es correcta, pero `obtenerPedidoConCuentaBloqueada` primero hace `findById` sin lock, luego bloquea Cuenta y vuelve a buscar Pedido con lock. Esa entidad puede estar ya administrada; el resultado del lock de Cuenta además se descarta. Un lock no equivale a refresh de estado/colecciones precargadas.
- **Riesgo concreto pendiente:** un contexto que leyó Cuenta ABIERTA/Pedido antes de esperar puede seguir validando ese estado después de que otra transacción pague. También dos ediciones pueden conservar un Pedido antiguo precargado. Se preparó `contextoPrecargadoAntesDelPagoNoDebePermitirEditarCuentaCerrada` con barreras, dos transacciones y una precarga legítima vía services; está diseñado para exigir rechazo y no ocultar el posible fallo. **No ejecutado; el riesgo se deduce del flujo y del cache de primer nivel, no se presenta como fallo PostgreSQL ya reproducido.** Hibernate documenta la [persistencia del estado en el contexto y refresh](https://docs.hibernate.org/orm/6.6/userguide/html_single/#pc-refresh).

No se agregó `synchronized`, global ni por recurso. No hay `synchronized` en producción actual. No se alteró producción por tratarse de una auditoría.

## 7. Snapshot de ItemPedido

Persistencia declarada y DDL real: `plato_id bigint`, `nombre_plato varchar(100)`, `precio_congelado numeric(10,2)`, `cantidad integer`, `combo boolean`, `bebida_incluida boolean`; todos NOT NULL. `platoId` no es relación a PlatoEntity y no hay FK de items_pedido hacia platos.

`agregarItem` copia los seis valores al agregar por primera vez; al repetir plato cambia sólo cantidad. `Pedido.calcularTotal` y `Cuenta.calcularTotal`, utilizados para pagar, multiplican precioCongelado × cantidad. Confirmar consulta disponibilidad actual, pero no reemplaza el precio. Está verificado localmente 20.000 → agregado a 20.000 → plato actualizado a 30.000 → pedido/pago a 20.000. La misma prueba con Plato realmente persistido y lectura tras reinicio quedó preparada.

## 8. RN-07 persistente y orden

`cambiarEstado` bloquea Pedido, valida usuario/transición/confirmación, crea CambioEstadoPedidoEntity con padre, ambos estados, usuario y fechaHora, agrega a historial y guarda Pedido. Cascade ALL incluye PERSIST/MERGE para generar la fila en `cambios_estado_pedido`. Cada una de las tres transiciones válidas agrega un registro distinto con IDENTITY.

`PedidoEntity.historialEstados` tiene **@OrderBy("id ASC")**: orden determinista al reconstruir, aun si dos fechas coinciden. El lock por Pedido serializa inserciones para ese mismo Pedido. Se verificaron localmente los campos de los tres cambios y se preparó count(*)=3 + reconstrucción desde un nuevo contexto después del reinicio. Sin credenciales no se afirma haber observado esas tres filas en PostgreSQL.

## 9. Pago, unicidad y cierre

Cuenta–Pago es OneToOne con FK NOT NULL. El DDL emitido contiene `pagos.cuenta_id bigint not null unique`; Hibernate deduplica la UNIQUE implícita del OneToOne y no conserva necesariamente el nombre `uk_pagos_cuenta` declarado en @Table. La unicidad efectiva sí aparece en el DDL; comprobar el catálogo por columnas, no sólo por nombre.

Monto se guarda como numeric(12,2). `registrarPago` tiene una única transacción: bloquea Cuenta, calcula total congelado, obtiene una fechaHora, marca CERRADA, asigna esa misma fecha a fechaCierre, guarda Cuenta y hace saveAndFlush(Pago). BusinessRuleException es RuntimeException: un fallo provoca rollback de ambos cambios con el proxy Spring real. Los mocks no realizan rollback; una verificación InOrder sólo prueba el orden de llamadas.

Duplicación concurrente: lock de Cuenta + UNIQUE de pago, pendientes de prueba real. FechaCierre == fechaHora se verifica también sobre ambos valores reconstruidos de PostgreSQL (timestamp(6), no comparar nanosegundos originales contra microsegundos persistidos). Cerrar transforma cuenta_abierta a NULL y permite una nueva Cuenta para la misma Mesa. La suite manual prueba dos cerradas históricas más una abierta, duplicate INSERT de cuenta/pago, orphanRemoval y rollback de pago/cierre.

## 10. Prueba manual PostgreSQL: comandos y flujo

No ejecutada: `DB_PASSWORD` estaba ausente. Usar una base dedicada de auditoría; los scripts/suite crean y conservan registros con identificadores/nombres nuevos, no borran datos. PostgreSQL debe estar corriendo y el usuario debe poder crear las tablas.

Crear la base manualmente (psql/createdb pedirá contraseña):

```powershell
createdb -h localhost -p 5432 -U postgres american_bites_s09_audit
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/american_bites_s09_audit'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'DB_PASSWORD' -AsSecureString)).Password
```

No imprimir la variable. Configurar las mismas variables en cada terminal que arranque la aplicación o ejecute las pruebas. Si ya existe la base dedicada, omitir createdb.

Terminal A, desde la raíz del repo:

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true'
```

Terminal B, con DB_PASSWORD también configurada:

```powershell
./scripts/s09-postgresql-smoke.ps1 -Stage BeforeRestart
```

Ese script ejecuta exactamente:

1. POST `/api/v1/mesas`: crea Mesa.
2. POST `/api/v1/mesas/{mesaId}/cuentas`: abre Cuenta.
3. POST `/api/v1/cuentas/{cuentaId}/pedidos`: crea Pedido.
4. POST `/api/v1/platos`: persiste Plato a 20.000; POST `/api/v1/pedidos/{pedidoId}/items` agrega ese ID persistido. PUT `/api/v1/platos/{platoId}` cambia a 30.000 como verificación adicional del snapshot.
5. POST `/api/v1/pedidos/{pedidoId}/confirmacion`.
6. PATCH `/api/v1/pedidos/{pedidoId}/estado`: EN_PREPARACION.
7. POST `/api/v1/cuentas/{cuentaId}/pago`: espera monto 20.000 y misma fecha de cierre/pago.
8. PATCH estado LISTO y luego ENTREGADO.
9. GET `/api/v1/pedidos/{pedidoId}/historial`: exige tres cambios con estados/usuarios/fechas en orden.
10. Detener Terminal A con Ctrl+C y arrancar de nuevo con el mismo comando y la misma base; no ejecutar clean ni borrar target entre fases.
11. Ejecutar el segundo comando: comprueba vía GET Mesa, Cuenta, Pedido, Item, Plato, Pago y los tres cambios; cantidades/totales/flags/fechas e IDs deben conservarse.
12. El segundo comando abre nueva Cuenta para esa misma Mesa y exige ID distinto/estado ABIERTA.

```powershell
./scripts/s09-postgresql-smoke.ps1 -Stage AfterRestart
```

Los IDs se guardan sin secretos en `target/s09-postgresql-smoke.json`. El script se verificó sintácticamente; no se ejecutó contra HTTP/PostgreSQL.

Para ejecutar las nueve pruebas reales (sin necesitar una aplicación web encendida):

```powershell
mvn '-Dtest=S09RegressionPostgresIT' '-Ds09.postgres=true' test
```

El flujo automatizado cierra el contexto completo, incluido EntityManagerFactory, arranca otro con el mismo PostgreSQL y comprueba las filas persistidas. La suite es manual y puede revelar fallos de locking que la auditoría dejó señalados; un fallo no debe descartarse porque las 224 unitarias pasan.

Para aislar la regresión de contexto precargado:

```powershell
mvn '-Dtest=S09RegressionPostgresIT#contextoPrecargadoAntesDelPagoNoDebePermitirEditarCuentaCerrada' '-Ds09.postgres=true' test
```

Revisar obligatoriamente el esquema real, incluso si arrancó sin error:

```powershell
psql -h localhost -p 5432 -U postgres -d american_bites_s09_audit
```

```sql
select column_name, data_type, is_nullable, is_generated, generation_expression
from information_schema.columns
where table_schema = current_schema() and table_name = 'cuentas'
  and column_name = 'cuenta_abierta'; -- debe ser boolean / ALWAYS / expresión CASE

select conrelid::regclass as tabla, conname, pg_get_constraintdef(oid)
from pg_constraint
where conrelid in ('cuentas'::regclass, 'pagos'::regclass)
  and contype = 'u'; -- UNIQUE (mesa_id, cuenta_abierta) y UNIQUE (cuenta_id)

select mesa_id, count(*) from cuentas where estado = 'ABIERTA'
group by mesa_id having count(*) > 1; -- cero filas
select cuenta_id, count(*) from pagos group by cuenta_id having count(*) > 1; -- cero filas

select c.id, c.estado, c.cuenta_abierta, c.fecha_cierre, p.id as pago_id,
       p.monto, p.fecha_hora, (c.fecha_cierre = p.fecha_hora) as misma_fecha
from cuentas c join pagos p on p.cuenta_id = c.id order by c.id;

select pedido_id, id, plato_id, nombre_plato, precio_congelado, cantidad,
       combo, bebida_incluida from items_pedido order by pedido_id, id;
select pedido_id, id, estado_anterior, estado_nuevo, usuario_responsable, fecha_hora
from cambios_estado_pedido order by pedido_id, id;
```

## 11. Verificación final

| Comando | Resultado |
| --- | --- |
| mvn clean test | BUILD SUCCESS; 224; 0 fallos, 0 errores, 0 omitidos; 38,885 s |
| mvn clean verify | BUILD SUCCESS; 224; 0 fallos, 0 errores, 0 omitidos; 49,987 s; JAR empaquetado |
| git diff --check | exit 0; sin errores de whitespace; avisos informativos LF→CRLF |
| git status --short | [Salida final completa](audit/s09-1-git-status.txt); cambios previos y artefactos de auditoría, sin staging/commit |

El primer intento Maven en sandbox falló por acceso al repositorio local; se repitió con permiso. La prueba inicial de generación DDL detectó que el nombre `uk_pagos_cuenta` no era emitido: se corrigió la aserción para comprobar la UNIQUE efectiva, no se cambió el esquema. También se corrigió el escape de argumentos separados por comas para PowerShell. Todas las comprobaciones finales deben corresponder a los fuentes finales.

No commit. No push. Trabajo detenido al entregar el informe; PostgreSQL y la aceptación de S09.1 quedan pendientes por los motivos documentados.
