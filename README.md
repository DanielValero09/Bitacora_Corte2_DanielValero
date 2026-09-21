# American Bites API

API REST académica para gestionar la operación del restaurante American Bites:
desde la carta y la disponibilidad de los productos hasta los pedidos de cocina,
el pago y el cierre de las cuentas de cada mesa.

Repositorio académico: `Bitacora_Corte2_DanielValero`.

## Tecnologías

- Java 21 y Maven.
- Spring Boot 3.5.16, Spring Web y Bean Validation.
- Lombok y MapStruct 1.6.3.
- Springdoc OpenAPI 2.8.17.
- JUnit 5, Mockito y MockMvc.
- JaCoCo 0.8.14.

## Arquitectura

El código se encuentra en `src/main/java/com/restaurante` y separa las
responsabilidades en las siguientes capas:

| Capa | Responsabilidad |
| --- | --- |
| `controller/` | Recibe solicitudes HTTP, valida los DTO de entrada y devuelve DTO de respuesta. |
| `service/` y `service/impl/` | Definen y ejecutan las reglas de negocio; encapsulan el almacenamiento en memoria. |
| `mapper/` | Transforma DTO y objetos del dominio mediante MapStruct. |
| `model/dto/request/` y `model/dto/response/` | Definen los contratos de entrada y salida de la API. |
| `model/domain/` | Representa ingredientes, platos, mesas, cuentas, pedidos, ítems, historial y pagos; calcula disponibilidad y totales. |
| `exception/` | Centraliza las excepciones y su traducción a respuestas HTTP. |
| `config/` | Describe la API para OpenAPI. |

Los servicios no dependen de DTO HTTP y el dominio no depende de Spring MVC.
Los datos se almacenan en mapas privados de los servicios, sin repositorios,
JPA ni base de datos. Las operaciones de escritura usan `synchronized`;
las modificaciones de contenido del pedido y los pagos comparten el monitor
global de `CuentaService` para coordinar la edición con el cálculo del pago y
el cierre de la cuenta.

Las clases `*MapperImpl` se generan durante la compilación en
`target/generated-sources/annotations`. No se mantienen manualmente.

## Funcionalidades

- Consulta de carta digital con platos activos y su disponibilidad actual.
- Gestión de ingredientes, platos, combos y desactivación lógica de platos.
- Disponibilidad compartida: agotar un ingrediente afecta a todos los platos que lo usan.
- Registro de mesas y apertura y consulta de cuentas.
- Creación de pedidos, gestión de cantidades e ítems y retiro de bebida de combos.
- Confirmación de pedidos y tablero de cocina.
- Cambios de estado e historial con usuario responsable y fecha.
- Registro y consulta de pagos, cierre de cuentas y nueva apertura para la misma mesa.

## Reglas de negocio principales

| Regla | Comportamiento |
| --- | --- |
| RN-01 | Los ítems de un pedido solo pueden editarse en `RECIBIDO`. |
| RN-02 | El precio se congela al agregar un producto; los cambios posteriores de la carta no alteran ese precio. |
| RN-03 | Un combo incluye bebida; retirarla conserva el precio y el subtotal. |
| RN-04 | Desde `EN_PREPARACION` no se permite editar el contenido del pedido. |
| RN-05 | Un pedido confirmado aparece en cocina; al quedar `ENTREGADO`, sale del tablero. |
| RN-06 | El único recorrido de estados es `RECIBIDO → EN_PREPARACION → LISTO → ENTREGADO`. |
| RN-07 | Cada transición registra estado anterior, estado nuevo, usuario responsable y fecha/hora. |

Confirmar no cambia el estado: el pedido sigue editable mientras permanezca en
`RECIBIDO` y la cuenta esté abierta. No se confirma un pedido vacío, ya confirmado
o con productos no disponibles. La entrada en preparación exige confirmación.

Una mesa solo puede tener una cuenta `ABIERTA`. Crear, editar o confirmar un
pedido exige una cuenta abierta. Las transiciones operativas de cocina son
independientes del estado de la cuenta, por lo que un pedido en preparación
puede terminar después del pago. Un plato no disponible no puede agregarse.
El pago toma el valor de `Cuenta.calcularTotal()` y cierra la cuenta;
no existe un endpoint independiente para cerrarla. Después puede abrirse una
nueva cuenta para la misma mesa. Esta versión permite pagar una cuenta vacía
o con pedidos pendientes: no se añade una condición de entrega previa al pago.

## Ejecución

Requisitos: JDK 21 y Maven disponibles en `PATH`. Ejecutar desde la raíz del proyecto:

```sh
mvn clean test
mvn clean verify
mvn spring-boot:run
```

La aplicación inicia en `http://localhost:8080`. Se detiene con `Ctrl+C`.
`verify` ejecuta las pruebas y genera el JAR ejecutable en `target/`.
No requiere archivos de configuración ni servicios externos; la primera
resolución de dependencias Maven puede necesitar acceso a Internet.

## Swagger

Con la aplicación iniciada, las rutas comprobadas para la versión instalada son:

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).
- Acceso alternativo: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html), que redirige a la interfaz.
- Documento OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

La documentación identifica el servicio como **American Bites API**, versión `v1`.

## Endpoints

Inventario de las 33 operaciones definidas en los controllers. Todas las rutas
de negocio tienen el prefijo `/api/v1`. La columna de errores muestra los
principales casos esperados; cualquier fallo inesperado produce un `500`
con mensaje genérico. Un identificador con formato no numérico produce `400`.

| Método | Ruta | Descripción | Éxito | Errores principales |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/ingredientes` | Crear ingrediente. | 201 | 400 entrada; 409 nombre duplicado |
| GET | `/api/v1/ingredientes` | Listar ingredientes. | 200 | — |
| GET | `/api/v1/ingredientes/{id}` | Consultar ingrediente. | 200 | 404 inexistente |
| PATCH | `/api/v1/ingredientes/{id}/disponibilidad` | Cambiar disponibilidad compartida. | 200 | 400 entrada; 404 inexistente |
| POST | `/api/v1/platos` | Crear plato y asociar ingredientes. | 201 | 400 entrada; 404 ingrediente; 409 nombre duplicado |
| GET | `/api/v1/platos` | Listar todos los platos, incluidos inactivos. | 200 | — |
| GET | `/api/v1/platos/{id}` | Consultar plato. | 200 | 404 inexistente |
| PUT | `/api/v1/platos/{id}` | Actualizar los campos editables del plato. | 200 | 400 entrada; 404 plato/ingrediente; 409 nombre duplicado |
| DELETE | `/api/v1/platos/{id}` | Desactivar lógicamente un plato. | 204 | 404 inexistente |
| GET | `/api/v1/carta` | Consultar platos activos, aun si no están disponibles. | 200 | — |
| POST | `/api/v1/mesas` | Crear mesa. | 201 | 400 entrada; 409 número duplicado |
| GET | `/api/v1/mesas` | Listar mesas. | 200 | — |
| GET | `/api/v1/mesas/{id}` | Consultar mesa. | 200 | 404 inexistente |
| POST | `/api/v1/mesas/{mesaId}/cuentas` | Abrir cuenta para la mesa. | 201 | 404 mesa; 409 cuenta abierta existente |
| GET | `/api/v1/mesas/{mesaId}/cuenta-abierta` | Consultar la cuenta abierta de la mesa. | 200 | 404 mesa o cuenta abierta |
| GET | `/api/v1/cuentas` | Listar cuentas. | 200 | — |
| GET | `/api/v1/cuentas/{id}` | Consultar cuenta y total. | 200 | 404 inexistente |
| POST | `/api/v1/cuentas/{cuentaId}/pedidos` | Crear pedido en una cuenta abierta. | 201 | 404 cuenta; 409 cuenta cerrada |
| GET | `/api/v1/cuentas/{cuentaId}/pedidos` | Listar los pedidos de la cuenta. | 200 | 404 cuenta |
| GET | `/api/v1/pedidos` | Listar pedidos. | 200 | — |
| GET | `/api/v1/pedidos/{id}` | Consultar pedido. | 200 | 404 inexistente |
| POST | `/api/v1/pedidos/{pedidoId}/items` | Agregar producto o incrementar su cantidad; devuelve el pedido. | 200 | 400 entrada; 404 pedido/plato; 409 estado, cuenta o disponibilidad |
| PATCH | `/api/v1/pedidos/{pedidoId}/items/{itemId}` | Actualizar cantidad. | 200 | 400 entrada; 404 pedido/ítem; 409 estado o cuenta |
| DELETE | `/api/v1/pedidos/{pedidoId}/items/{itemId}` | Retirar ítem. | 204 | 404 pedido/ítem; 409 estado o cuenta |
| PATCH | `/api/v1/pedidos/{pedidoId}/items/{itemId}/bebida` | Retirar bebida de un combo sin cambiar precio. | 200 | 404 pedido/ítem; 409 estado, cuenta o producto no combo |
| POST | `/api/v1/pedidos/{pedidoId}/confirmacion` | Confirmar pedido. | 200 | 404 recurso; 409 estado, cuenta, confirmación previa, pedido vacío o disponibilidad |
| PATCH | `/api/v1/pedidos/{pedidoId}/estado` | Avanzar al siguiente estado y registrar historial. | 200 | 400 entrada; 404 pedido; 409 transición o falta de confirmación |
| GET | `/api/v1/pedidos/{pedidoId}/historial` | Consultar cambios de estado. | 200 | 404 pedido |
| GET | `/api/v1/cocina/pedidos` | Listar confirmados que aún no están entregados. | 200 | — |
| POST | `/api/v1/cuentas/{cuentaId}/pago` | Registrar pago total y cerrar cuenta. | 201 | 404 cuenta; 409 cuenta cerrada o pago duplicado |
| GET | `/api/v1/cuentas/{cuentaId}/pago` | Consultar pago de una cuenta. | 200 | 404 cuenta o pago |
| GET | `/api/v1/pagos` | Listar pagos. | 200 | — |
| GET | `/api/v1/pagos/{id}` | Consultar pago. | 200 | 404 inexistente |

El tablero de cocina solo consulta pedidos; las transiciones se ejecutan mediante
`PedidoController`, en `/api/v1/pedidos/{pedidoId}/estado`.

## Validaciones y errores

Los DTO validan nombres obligatorios (máximo 100 caracteres), descripciones
opcionales (máximo 1000), precio mínimo `0.01`, cantidades e identificadores
de ingredientes positivos y campos obligatorios no nulos. La lista de
ingredientes puede estar vacía. El usuario responsable es obligatorio y
admite hasta 100 caracteres.

`ErrorResponse` mantiene los campos `timestamp`, `status`, `code`, `message`,
`path` y `fieldErrors`. La validación de campos devuelve `400 / VALIDATION_ERROR`;
JSON ilegible, cuerpo obligatorio ausente, enum desconocido e identificadores
no numéricos devuelven `400 / INVALID_REQUEST`. Recursos inexistentes producen
`404`; duplicados y reglas de negocio o transiciones inválidas, `409`.
El `500 / INTERNAL_ERROR` no expone detalles internos.

## Pruebas y cobertura

Las pruebas usan JUnit 5 y Mockito para dominio y servicios, MockMvc para los
contratos HTTP, integración entre servicios reales y una prueba de carga del
contexto Spring Boot. Los escenarios de integración cubren carta, precio
congelado, combos, cocina, historial, pago y reapertura de cuentas. También se
comprueban operaciones concurrentes, entradas inválidas y rechazo de cambios
después del cierre. Las pruebas no necesitan red ni base de datos.

JaCoCo genera el reporte al ejecutar `mvn clean test` o `mvn clean verify`:

```text
target/site/jacoco/index.html
```

El reporte contiene instrucciones, líneas, ramas, métodos y clases. Se consulta
el resultado generado para conocer la cobertura actual; no se fija un
porcentaje permanente en este README. No se busca cubrir artificialmente
`main()`, getters/setters ni defensas contra `null` del código generado.

## Persistencia

Los datos se mantienen en memoria y se pierden al reiniciar la aplicación.
Esta versión no utiliza base de datos ni carga datos iniciales automáticamente.

## Diagramas y auditoría

- [Diagrama de clases del dominio](docs/diagrams/class-diagram.puml).
- [Secuencia del flujo de pedido, cocina y pago](docs/diagrams/order-flow-sequence.puml).
- [Informe de auditoría final](docs/final-audit.md).

Los diagramas se entregan como fuentes PlantUML, sin imágenes binarias ni
dependencias adicionales en Maven. Las referencias por ID se distinguen de
las asociaciones entre objetos.
