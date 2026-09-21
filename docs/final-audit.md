# Auditoría final — American Bites API

Etapa 10. Verificación realizada el 21 de septiembre de 2026 sobre la base
versionada `a596838`, con el árbol de trabajo inicialmente limpio.

## 1. Arquitectura

Se conserva la estructura `controller`, `service`, `service/impl`,
`model/domain`, `model/dto/request`, `model/dto/response`, `mapper` y `exception`.
Los controllers reciben/devuelven DTO y delegan las reglas. Los servicios
trabajan con dominio y parámetros simples; no importan DTO HTTP. Los mappers
transforman datos y delegan los cálculos al dominio. El dominio no importa
Spring MVC. El almacenamiento está en mapas privados de los servicios.

No hay Repository, JPA, JDBC ni base de datos. No se encontraron ciclos de
inyección: Pedido depende de Cuenta y Plato; Pago de Cuenta; Cuenta de Mesa;
Plato de Ingrediente. No se movieron archivos ni se refactorizaron streams.
Se añadió únicamente `config/OpenApiConfig` para describir la API.

## 2. Reglas de negocio

| Regla | Resultado y evidencia |
| --- | --- |
| RN-01 | Edición de ítems restringida a RECIBIDO, incluso si el pedido ya está confirmado. Verificada en pruebas de servicio, HTTP e integración. |
| RN-02 | El ítem conserva precio y nombre al agregarse; agregar de nuevo el mismo plato incrementa cantidad y mantiene el precio original. Verificado hasta el pago. |
| RN-03 | Combo inicia con bebida; retirarla no cambia precio ni subtotal. Verificado en dominio, servicio e integración. |
| RN-04 | EN_PREPARACION, LISTO y ENTREGADO rechazan edición de contenido. |
| RN-05 | Cocina incluye confirmados no entregados y excluye los no confirmados. |
| RN-06 | Solo RECIBIDO → EN_PREPARACION → LISTO → ENTREGADO; sin saltos, retrocesos o repeticiones. Preparación exige confirmación. |
| RN-07 | Cada transición válida agrega anterior, nuevo, usuarioResponsable y fechaHora; las rechazadas no alteran historial. |

También se verificaron: rechazo de platos no disponibles; efecto de ingredientes
compartidos; una sola cuenta ABIERTA por mesa; cuenta abierta para crear, editar
y confirmar; transiciones de cocina independientes del cierre; cierre únicamente como consecuencia del pago;
uso de `Cuenta.calcularTotal()`; reapertura de una nueva cuenta tras el cierre.

El pago no cambia EstadoPedido ni impide terminar el flujo de cocina. No se
impuso que todos los pedidos deban estar entregados para pagar ni se prohibió
pagar cuentas vacías.

## 3. Inventario final de endpoints

El [inventario completo del README](../README.md#endpoints) documenta método,
ruta, propósito, éxito y errores principales de las **33 operaciones reales**.
Se contrastó automáticamente con `/v3/api-docs` del JAR en ejecución: coincidencia
de los 33 pares método/ruta. Se comprobaron los códigos de éxito publicados.

| Área del controller | Operaciones |
| --- | ---: |
| Ingredientes | 4 |
| Platos | 5 |
| Carta | 1 |
| Mesas y apertura/consulta de su cuenta | 5 |
| Cuentas | 2 |
| Pedidos | 11 |
| Cocina | 1 |
| Pagos | 4 |

Todas las rutas de negocio usan `/api/v1`. Se conservaron 201 para creación de
ingredientes, platos, mesas, cuentas, pedidos y pagos; 200 para consultas y
modificaciones; 204 para desactivar platos y eliminar ítems. Agregar ítems y
confirmar devuelven 200 con el pedido, como en el contrato existente.

## 4. Validaciones

Se revisaron los ocho DTO request y su uso con `@Valid`:

| DTO | Restricciones existentes conservadas |
| --- | --- |
| CrearIngredienteRequest | Nombre no blanco, máximo 100; disponibilidad no nula. |
| CambiarDisponibilidadIngredienteRequest | Disponibilidad no nula. |
| CrearPlatoRequest / ActualizarPlatoRequest | Nombre no blanco, máximo 100; descripción opcional hasta 1000; precio no nulo y mínimo 0.01; combo y lista de ingredientes no nulos; IDs no nulos y positivos. |
| CrearMesaRequest | Número no nulo y positivo. |
| AgregarItemPedidoRequest | platoId y cantidad no nulos y positivos. |
| ActualizarCantidadItemRequest | Cantidad no nula y positiva. |
| CambiarEstadoPedidoRequest | Estado no nulo; usuario no blanco, máximo 100. |

No se añadieron restricciones arbitrarias. Se mantienen listas de ingredientes
vacías válidas. Bean Validation devuelve 400. Se corrigieron los casos que
fallan antes de Bean Validation: JSON ilegible, cuerpo requerido ausente,
tipos incompatibles, enum desconocido e ID de ruta no numérico.

## 5. Manejo de errores

| Excepción | HTTP | Código |
| --- | ---: | --- |
| ResourceNotFoundException | 404 | RESOURCE_NOT_FOUND |
| ResourceAlreadyExistsException | 409 | RESOURCE_ALREADY_EXISTS |
| BusinessRuleException | 409 | BUSINESS_RULE_VIOLATION |
| InvalidOrderStateException | 409 | INVALID_ORDER_STATE |
| MethodArgumentNotValidException | 400 | VALIDATION_ERROR |
| HttpMessageNotReadableException / MethodArgumentTypeMismatchException | 400 | INVALID_REQUEST |
| Exception | 500 | INTERNAL_ERROR |

Se conservan `timestamp`, `status`, `code`, `message`, `path` y `fieldErrors`.
Los errores inesperados devuelven un mensaje genérico; la prueba existente
comprueba que no se filtran detalles internos. No se imprimen stack traces con
System.out: el manejador registra la excepción inesperada con SLF4J a nivel ERROR.

## 6. Logging

Los seis servicios y el manejador usan `@Slf4j`. INFO registra operaciones
relevantes; WARN, rechazos de recursos/reglas/entrada; ERROR, excepciones
inesperadas. Solo se añadió el WARN correspondiente al nuevo manejo de formato
inválido, sin registrar el cuerpo recibido. No se encontraron credenciales
reales en los logs definidos por el código. El texto `credencial-secreta` de la
prueba del manejador es un dato ficticio para verificar la respuesta 500.

## 7. OpenAPI y Swagger

Springdoc 2.8.17 funciona con las versiones del POM. `OpenApiConfig` aporta el
título American Bites API, la descripción solicitada y versión v1. No se
añadieron anotaciones repetitivas a los controllers.

## 8. README

Se sustituyó el encabezado inicial por documentación académica de tecnologías,
arquitectura, funcionalidades, reglas, ejecución, Swagger, inventario HTTP,
validaciones, pruebas, JaCoCo y persistencia. Se conserva el identificador
`Bitacora_Corte2_DanielValero` sin inventar autores. El README no fija porcentajes
de cobertura que puedan quedar obsoletos.

## 9. Diagramas PlantUML

- `docs/diagrams/class-diagram.puml`: las ocho clases del dominio y los dos enums,
  asociaciones reales y dependencias diferenciadas para mesaId, cuentaId y platoId.
- `docs/diagrams/order-flow-sequence.puml`: crear pedido, agregar producto,
  confirmar, consultar cocina, avanzar estados, pagar y cerrar cuenta.

El diagrama de secuencia ubica las transiciones en PedidoController, no en
CocinaController. Los mapas se representan dentro de sus servicios. Se revisó
la correspondencia con el código. Se entregan fuentes; no se instaló PlantUML,
no se generaron PNG/PDF y no se ejecutó un renderizador de los diagramas.

## 10. POM y configuración

Java 21; Spring Boot 3.5.16; MapStruct 1.6.3; Springdoc 2.8.17; JaCoCo 0.8.14.
Se conservaron el plugin Spring Boot, los procesadores Lombok, MapStruct y
`lombok-mapstruct-binding`, y JaCoCo con `prepare-agent` y reporte en fase test.
Las pruebas de integración existentes terminan en `Test` y las ejecuta Surefire.

El POM no contiene JPA, Hibernate ORM, JDBC, Security, H2 ni drivers de base de
datos. El JAR sí contiene `hibernate-validator`, implementación necesaria de
Bean Validation: no es Hibernate ORM. No se cambió ninguna versión o dependencia.
`src/main/resources` permanece vacío; no se creó configuración innecesaria.

## 11. .gitignore y seguridad del repositorio

Ya estaban ignorados `target/`, `.idea/`, `*.iml` y `*.log`. Se añadieron `logs/`,
temporales comunes y archivos `.env`, permitiendo un eventual `.env.example`.
No se detectaron secretos reales en el árbol de trabajo auditado. No hay target,
logs ni temporales en el inventario de archivos versionados.

Advertencia pendiente: **siete archivos de `.idea` ya estaban versionados**:
`.gitignore`, `Bitacora_Corte2_DanielValero.iml`, `compiler.xml`, `encodings.xml`,
`jarRepositories.xml`, `misc.xml` y `vcs.xml`. `.gitignore` no deja de versionarlos
retroactivamente. Se conservaron sin ejecutar `git rm`. Además, `misc.xml` declara
un nivel de lenguaje JDK_25 aunque el POM y las verificaciones usan Java 21;
no se modificó la configuración personal del IDE.

## 12. Residuos y determinismo

No se encontraron TODO, FIXME, System.out.print, System.err.print,
printStackTrace, bloques grandes comentados ni comentarios temporales en src.
Se eliminó un import sin uso de `ArrayList` en PedidoHttpTest.

No se encontraron pruebas deshabilitadas, dependencias de orden declaradas,
llamadas de red, base de datos, sleeps ni generación aleatoria. Las pruebas
concurrentes usan coordinación explícita. La nueva regresión de pago/edición
controla la intercalación con latches y una condición observable, con plazos
para detectar bloqueos. No se eliminó ninguna prueba previa.

Los logs locales de etapas anteriores se conservaron; están ignorados.
Los logs de esta auditoría también están ignorados y sirven como evidencia local.

## 13–14. Cambios productivos y justificación

| Archivo | Cambio y evidencia |
| --- | --- |
| GlobalExceptionHandler.java | Traduce fallos de lectura del cuerpo y conversión de parámetros a 400. Cinco regresiones devolvían 500 antes de la corrección. Conserva la forma de ErrorResponse y oculta los detalles de deserialización. |
| PedidoServiceImpl.java | Coordina creación, modificaciones de contenido y confirmación con el monitor compartido de CuentaService. `cambiarEstado` conserva su sincronización propia, sin depender del estado de la cuenta. |
| PagoServiceImpl.java | Usa el mismo monitor durante validación, cálculo, registro y cierre. Antes, el pago concurrente podía ser 0 mientras el total final de la cuenta era 20.00. La regresión reproduce esa intercalación y ahora conserva el monto correcto. |
| config/OpenApiConfig.java | Añade metadatos descriptivos ausentes, comprobados contra el documento servido. No agrega endpoints ni reglas de negocio. |

Se mantienen los synchronized existentes. La apertura de cuentas usa el mismo
objeto como monitor. No hay infraestructura nueva ni dependencias circulares;
las escrituras que pueden afectar el total y los pagos quedan serializadas junto
con la apertura. Las transiciones de cocina no toman ese monitor compartido.
El aumento de líneas del diff en servicios se debe principalmente a la sangría
del bloque synchronized, sin reestructurar la lógica de negocio restante.

## 15. Archivos creados

- `src/main/java/com/restaurante/config/OpenApiConfig.java`
- `docs/diagrams/class-diagram.puml`
- `docs/diagrams/order-flow-sequence.puml`
- `docs/final-audit.md`

## 16. Archivos modificados

- `.gitignore`
- `README.md`
- `src/main/java/com/restaurante/exception/GlobalExceptionHandler.java`
- `src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java`
- `src/main/java/com/restaurante/service/impl/PagoServiceImpl.java`
- `src/test/java/com/restaurante/controller/MesaCuentaHttpTest.java`
- `src/test/java/com/restaurante/controller/PedidoHttpTest.java`
- `src/test/java/com/restaurante/service/impl/PagoIntegrationTest.java`

Los artefactos Maven, el reporte JaCoCo y los logs locales no forman parte de
esta lista de fuentes para entrega y no se versionaron.

## 17–19. Pruebas y comandos finales

La base original pasó 214 pruebas. El resultado final añade siete ejecuciones de
regresión: tres cuerpos ilegibles, un ID no numérico, un enum inválido, una
intercalación pago/edición y un escenario de pago durante la preparación. Este
último demuestra conjuntamente que cocina puede terminar el pedido después del
cierre y que agregar, actualizar o eliminar ítems sigue rechazado en otro pedido
RECIBIDO de la cuenta cerrada.

| Comando | Resultado | Pruebas | Fallos | Errores | Omitidas | Tiempo Maven | Salida |
| --- | --- | ---: | ---: | ---: | ---: | --- | ---: |
| `mvn clean test` | BUILD SUCCESS | 221 | 0 | 0 | 0 | 16.932 s | 0 |
| `mvn clean verify` | BUILD SUCCESS | 221 | 0 | 0 | 0 | 18.291 s | 0 |

Resultados finales terminados a las 15:39:20 y 15:39:56, respectivamente,
hora America/Bogota. Surefire confirma los totales en sus XML. `verify` produjo
`target/restaurante-0.0.1-SNAPSHOT.jar` y ejecutó el reempaquetado Spring Boot.

El primer intento dentro del entorno restringido no pudo acceder al repositorio
Maven local. Las verificaciones se ejecutaron después con los permisos aprobados,
sin cambiar el POM ni la configuración del proyecto para sortear el entorno.

Evidencia local: `etapa10-baseline.log`, `etapa10-regresion-antes.log`,
`etapa10-regresion-despues.log`, `etapa10-test.log` y `etapa10-verify.log`.

## 20. Cobertura JaCoCo de esta entrega

Se revisaron `target/site/jacoco/index.html` y sus contadores en `jacoco.xml`
después del último verify. Los porcentajes siguientes usan los contadores
exactos; la interfaz HTML presenta algunos porcentajes como enteros.

| Métrica | Cubiertos | Total | Sin cubrir | Cobertura |
| --- | ---: | ---: | ---: | ---: |
| Instrucciones | 2794 | 2827 | 33 | 98,83 % |
| Líneas | 595 | 610 | 15 | 97,54 % |
| Ramas | 111 | 128 | 17 | 86,72 % |
| Métodos | 163 | 164 | 1 | 99,39 % |
| Clases | 52 | 52 | 0 | 100 % |

Estos son resultados fechados de la entrega, no una garantía para cambios futuros.

## 21. Clases con menor cobertura

Porcentaje de instrucciones:

| Clase | Cobertura | Explicación |
| --- | ---: | --- |
| RestauranteApplication | 37,50 % | main() no se invoca desde la suite; el JAR sí se arrancó en una comprobación separada, fuera de la medición JaCoCo. |
| MesaMapperImpl | 89,74 % | Rutas defensivas contra null generadas por MapStruct. |
| IngredienteMapperImpl | 92,59 % | Rutas defensivas generadas. |
| PagoMapperImpl | 94,59 % | Ruta de entrada null generada. |
| PlatoMapperImpl | 94,97 % | Rutas de entradas/colecciones null generadas. |
| PedidoMapperImpl | 95,60 % | Rutas de entradas/colecciones null generadas. |
| CuentaMapperImpl | 95,74 % | Ruta de entrada null generada. |
| ItemPedidoMapperImpl | 96,61 % | Ruta de entrada null generada. |
| GlobalExceptionHandler | 99,33 % | Una rama del desempate lexicográfico de mensajes para un mismo campo no se ejecuta. |

Los seis servicios alcanzan 100 % de instrucciones y ramas en esta ejecución.
No se editaron MapperImpl ni se añadieron pruebas triviales para main,
getters/setters o código generado. No se cambiaron las exclusiones de cobertura.

## 22–23. Arranque y comprobación HTTP real

Se ejecutó el JAR con Java 21, dirección 127.0.0.1 y puerto efímero (51274), para
no interferir con el puerto de trabajo del usuario. Spring Boot inició en
2.348 segundos, sin errores de contexto ni startup.

| Ruta | Resultado real |
| --- | --- |
| `/v3/api-docs` | 200; título American Bites API; versión v1; 33 operaciones. |
| `/v3/api-docs/swagger-config` | 200. |
| `/swagger-ui/index.html` | 200. |
| `/swagger-ui/swagger-ui.css` | 200. |
| `/swagger-ui.html` | 302; Location: /swagger-ui/index.html. |
| `/api/v1/carta` | 200. |

Se detuvieron los lanzadores y sus JVM hijas; se verificó que quedaban **cero
procesos del JAR auditado**. El primer intento del script encontró un log aún
vacío; se corrigió la lectura del script, sin modificar la aplicación.
No se dejó un servidor ejecutándose. Logs de arranque en `target/etapa10-startup.log`
y `target/etapa10-startup-error.log`.

## 24. git diff --check

Finaliza con código 0, sin errores de espacios en blanco. Git avisa de la
normalización LF → CRLF configurada en este entorno; son advertencias de finales
de línea, no errores del diff.

## 25. Advertencias restantes

- Mockito/Byte Buddy: autoacoplamiento de agente dinámico, restricción futura
  anunciada por el JDK y advertencia de compartición de clases (CDS). Las pruebas
  pasan en Java 21; no se modificó el POM para silenciar advertencias.
- Springdoc advierte que OpenAPI y Swagger están habilitados por defecto;
  es el comportamiento requerido para esta entrega.
- Siete archivos de `.idea` siguen versionados; uno declara JDK_25. Se respetó
  la prohibición de eliminarlos automáticamente del índice.
- Git puede mostrar la advertencia LF → CRLF al revisar archivos modificados.

Los WARN de rechazos y el ERROR sintético de la prueba del manejador son salidas
esperadas de pruebas negativas, no fallos de la suite.

## 26. Estado del repositorio al entregar

```text
 M .gitignore
 M README.md
 M src/main/java/com/restaurante/exception/GlobalExceptionHandler.java
 M src/main/java/com/restaurante/service/impl/PagoServiceImpl.java
 M src/main/java/com/restaurante/service/impl/PedidoServiceImpl.java
 M src/test/java/com/restaurante/controller/MesaCuentaHttpTest.java
 M src/test/java/com/restaurante/controller/PedidoHttpTest.java
 M src/test/java/com/restaurante/service/impl/PagoIntegrationTest.java
?? docs/
?? src/main/java/com/restaurante/config/
```

No se ejecutaron commit, push, merge, rebase, reset ni git rm. La etapa termina
con los cambios disponibles para revisión y entrega; no se inicia otra etapa.
