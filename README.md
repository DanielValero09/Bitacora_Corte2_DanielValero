# American Bites API

API REST académica para gestionar la operación del restaurante American Bites:
desde la carta y la disponibilidad de los productos hasta los pedidos de cocina,
el pago y el cierre de las cuentas de cada mesa.

Repositorio académico: `Bitacora_Corte2_DanielValero`.

## Seguridad

S10.1 usa **JWT Bearer**, Spring Security y BCrypt. La API es stateless:
el cliente envía `Authorization: Bearer <token>` en cada operación protegida.
No usa HTTP Basic, sesiones ni OAuth2. Las 33 operaciones anteriores conservan
sus rutas, DTO y reglas RN-01 a RN-07; se añade `POST /api/v1/auth/login`.

| Rol/authority | Permisos |
| --- | --- |
| `ROLE_GERENTE` | Ingredientes, platos, alta de mesas y supervisión operativa de sala/cocina/pagos. |
| `ROLE_MESERO` | Consultar mesas, abrir/consultar cuentas, crear/editar/confirmar pedidos, entregar y cobrar. |
| `ROLE_COCINERO` | Tablero, detalle e historial de pedidos; pasar a EN_PREPARACION y LISTO. |
| `ROLE_CLIENTE` | Carta pública; sin administración ni acceso a cuentas ajenas. |
| Anónimo | GET `/api/v1/carta`, POST `/api/v1/auth/login`, Swagger UI y OpenAPI. |

Ver [matriz completa por método y ruta](docs/security/role-matrix.md) y
[checklist OWASP con pendientes](docs/security/owasp-checklist.md).
El endpoint compartido de estados exige COCINERO para preparación/listo,
MESERO para entrega y permite GERENTE sujeto a las mismas reglas de negocio.
Los roles se almacenan literalmente con un único prefijo `ROLE_` en BD y JWT.

### Variables de entorno y primer GERENTE

PostgreSQL debe estar disponible en `localhost:5432/american_bites`, usuario
`postgres` (configuración existente), con `ddl-auto=update`.

| Variable | Uso |
| --- | --- |
| `DB_PASSWORD` | Contraseña PostgreSQL; obligatoria para arrancar con BD real. |
| `JWT_SECRET` | Clave secreta Base64 de al menos 32 bytes aleatorios; obligatoria. |
| `JWT_EXPIRATION_MS` | Tiempo de vida del token; predeterminado `3600000`, debe ser positivo. |
| `BOOTSTRAP_ADMIN_EMAIL` | Email del primer GERENTE, opcional. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Contraseña inicial, opcional, máximo 72 bytes UTF-8. |

Ejemplo PowerShell: pide las contraseñas sin mostrarlas. Generar `JWT_SECRET`
una vez y conservarlo en configuración segura para reinicios posteriores;
regenerarlo invalida los JWT previos. No copiarlo al repositorio.

```powershell
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password PostgreSQL' -AsSecureString)).Password
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtKeyBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
$env:JWT_EXPIRATION_MS = '3600000'
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Email del primer GERENTE'
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password del primer GERENTE' -AsSecureString)).Password
mvn spring-boot:run
```

El bootstrap solo crea un usuario cuando **ambas variables están definidas y
no vacías** y ese email aún no existe (búsqueda sin distinguir mayúsculas).
Normaliza el email, guarda `passwordEncoder.encode(...)`, `ROLE_GERENTE` y
`activo=true`. No restablece contraseñas, roles ni estado de usuarios existentes.
Sin las variables de bootstrap la aplicación arranca normalmente si BD y JWT
están configurados. El hash BCrypt nunca se devuelve en un DTO ni se registra.
Después del alta, retirar las variables de bootstrap antes del siguiente arranque:

```powershell
Remove-Item Env:BOOTSTRAP_ADMIN_EMAIL,Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
```

`.env` y `*.p12` están ignorados. Spring Boot no carga `.env` automáticamente:
las variables deben existir en el proceso que ejecuta Maven/Java. No hay
endpoint de registro ni administración de usuarios en esta etapa.

### HTTPS local (SSL/TLS)

HTTP sigue disponible por defecto en `http://localhost:8080` cuando
`SSL_ENABLED` no existe o vale `false`. `server.ssl.enabled=${SSL_ENABLED:false}`
controla SSL y una importación opcional carga `application-ssl-true.properties`
únicamente con `SSL_ENABLED=true` (usar exactamente `true` en minúsculas).
Ese archivo configura el puerto `8443`, `classpath:restaurante.p12`, tipo
`PKCS12`, alias `restaurante` y password `${SSL_KEYSTORE_PASSWORD}` sin valor
predeterminado. No se necesita activar otro perfil ni definir otro puerto.
La configuración usa [importaciones y placeholders de Spring Boot](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

Crear el certificado **manualmente**, desde la raíz del proyecto en PowerShell
con `keytool` del JDK disponible en `PATH`:

```powershell
keytool -genkeypair -alias restaurante -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore 'src/main/resources/restaurante.p12' -validity 365 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1'
```

`keytool` solicita y confirma la contraseña de forma interactiva; elegir una
contraseña privada y conservarla fuera del repositorio. El certificado es
autofirmado, con RSA de 2048 bits y validez de 365 días. No pasar la contraseña
como argumento del comando ni versionar `restaurante.p12`.

En la misma sesión PowerShell, definir las variables y arrancar HTTPS:

```powershell
$env:SSL_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password del keystore (la misma usada en keytool)' -AsSecureString)).Password
$env:SSL_ENABLED = 'true'
mvn spring-boot:run
```

`SSL_KEYSTORE_PASSWORD` es obligatoria solo con HTTPS y debe coincidir con la
contraseña del keystore. También se necesitan PostgreSQL, `DB_PASSWORD` y
`JWT_SECRET` descritos arriba. No guardar valores reales en archivos del
repositorio. `.gitignore` ya excluye `*.p12` y `.env`; las variables se definen
en el proceso, pues Spring Boot no carga `.env` automáticamente.

Abrir [Swagger HTTPS](https://localhost:8443/swagger-ui/index.html).
El navegador mostrará una **advertencia de confianza porque el certificado es
autofirmado**; aceptar la excepción para esta prueba local. Ejecutar login,
copiar el token y usar **Authorize → bearerAuth** como en HTTP. Comprobar una
operación protegida sin token (401), con un rol insuficiente (403) y con un
rol autorizado. Swagger/OpenAPI, JWT, CORS y headers conservan su configuración.

Para volver a HTTP, detener la aplicación con `Ctrl+C` y ejecutar:

```powershell
$env:SSL_ENABLED = 'false'
Remove-Item Env:SSL_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
mvn spring-boot:run
```

Las pruebas automatizadas fijan `server.ssl.enabled=false`; no requieren un
archivo `.p12` ni contraseña SSL. No se genera un certificado de pruebas.

### Login y Swagger Authorize

Enviar sin token:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"<email provisionado>","password":"<contraseña provisionada>"}
```

La respuesta 200 tiene `token`, `tipo: "Bearer"` y `expirationMs`. El token
contiene subject=email, claim `rol`, issuedAt y expiration, firmado con HMAC.
En [Swagger](http://localhost:8080/swagger-ui/index.html), ejecutar login,
copiar únicamente el token, pulsar **Authorize**, pegarlo en `bearerAuth`
y confirmar. Swagger añade el prefijo Bearer. Login y carta funcionan sin
Authorize; solo las operaciones protegidas declaran el requisito de seguridad.

- **401 / UNAUTHORIZED**: falta autenticación, token inválido/expirado o
  credenciales incorrectas. Login no distingue usuario inexistente de password
  incorrecto. Un usuario inactivo falla también con un JWT previo.
- **403 / FORBIDDEN**: usuario autenticado con rol insuficiente; también se
  rechazan peticiones de orígenes CORS no permitidos.
- Ambos usan `ErrorResponse` JSON, sin HTML ni stack trace. El resto de errores
  de validación/negocio conserva los contratos previos.

En cada petición JWT se carga el usuario activo desde BD y se contrasta el rol;
si cambia el rol, el token previo deja de autenticar y se requiere otro login.
No hay refresh token ni endpoint de logout: el cliente descarta su token.

CORS de desarrollo permite únicamente `http://localhost:3000` y
`http://localhost:5173`, métodos GET/POST/PUT/PATCH/DELETE/OPTIONS y headers
Authorization, Content-Type y Accept. No habilita cookies/credentials.
Headers: `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y CSP
con recursos del mismo origen; permite estilos inline usados por Swagger,
imágenes data y bloquea objetos y frames. La comprobación visual de CSP en
navegador sigue pendiente; los recursos Swagger se comprueban automáticamente.

### Evidencias visuales pendientes de añadir manualmente

No se incluyen capturas inventadas. Añadir tras arrancar con PostgreSQL real:

- Login exitoso (ocultar JWT completo al compartir capturas).
- Respuesta 401 sin autenticación y 403 con rol insuficiente.
- Botón Swagger Authorize y ejecución de una operación autorizada.
- Headers de seguridad en las herramientas del navegador; confirmar que CSP no rompe Swagger.
- Hash BCrypt en `usuarios.password` en BD, ocultando el hash al compartir evidencia.
- Persistencia del usuario y login después del reinicio; usuario inactivo rechazado.

La provisión de usuarios MESERO/COCINERO/CLIENTE para evidencia manual requiere
un procedimiento de administración que almacene BCrypt; el bootstrap solo
provisiona GERENTE. No se añade un endpoint de registro público.

## Tecnologías

- Java 21 y Maven.
- Spring Boot 3.5.16, Spring Web y Bean Validation.
- Lombok y MapStruct 1.6.3.
- Springdoc OpenAPI 2.8.17.
- Spring Security y JJWT 0.12.3.
- PostgreSQL y Spring Data JPA.
- JUnit 5, Mockito y MockMvc.
- JaCoCo 0.8.14.

## Arquitectura

El código se encuentra en `src/main/java/com/restaurante` y separa las
responsabilidades en las siguientes capas:

| Capa | Responsabilidad |
| --- | --- |
| `controller/` | Recibe solicitudes HTTP, valida los DTO de entrada y devuelve DTO de respuesta. |
| `service/` y `service/impl/` | Definen y ejecutan las reglas de negocio con repositorios JPA y transacciones. |
| `repository/` y `model/entity/` | Repositorios y entidades de persistencia PostgreSQL, incluido Usuario. |
| `security/` | JWT, UserDetails, bootstrap y respuestas de seguridad. |
| `mapper/` | Transforma DTO y objetos del dominio mediante MapStruct. |
| `model/dto/request/` y `model/dto/response/` | Definen los contratos de entrada y salida de la API. |
| `model/domain/` | Representa ingredientes, platos, mesas, cuentas, pedidos, ítems, historial y pagos; calcula disponibilidad y totales. |
| `exception/` | Centraliza las excepciones y su traducción a respuestas HTTP. |
| `config/` | Describe la API para OpenAPI. |

Los servicios no dependen de DTO HTTP y el dominio no depende de Spring MVC.
Los datos se almacenan mediante JPA en PostgreSQL. Las operaciones de escritura
usan transacciones y locks de BD para coordinar la edición con el cálculo del
pago y el cierre de cuenta. Los mapas de fixtures pertenecen únicamente a tests.

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

La aplicación inicia por defecto en `http://localhost:8080`; con SSL habilitado,
en `https://localhost:8443` según la sección Seguridad. Se detiene con `Ctrl+C`.
`verify` ejecuta las pruebas y genera el JAR ejecutable en `target/`.
Requiere PostgreSQL y las variables DB_PASSWORD/JWT_SECRET descritas en Seguridad.
La primera resolución de dependencias Maven puede necesitar acceso a Internet.

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
Se añade login como operación 34. Las operaciones protegidas también pueden
responder 401/403; sus permisos se detallan en la matriz de seguridad enlazada.

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
después del cierre. Las pruebas normales no necesitan BD; las suites PostgreSQL
opt-in requieren credenciales y BD dedicada. S10 añade pruebas de login/JWT,
roles, usuario inactivo, BCrypt, CORS, headers y Swagger. El secreto fijo está
exclusivamente en test y nunca debe utilizarse para arrancar la aplicación real.
Los HTTP de negocio ejecutan ahora filtros y proxies de seguridad como GERENTE;
el test del exception handler usa `@WithMockUser` y la configuración real.

Para las 9 pruebas PostgreSQL S09 conservadas y la nueva prueba de Usuario:

```powershell
mvn '-Dtest=S09RegressionPostgresIT,S10UsuarioPostgresIT' '-Ds09.postgres=true' '-Ds10.postgres=true' test
```

No las ejecutar sobre una BD de producción; conservan sus filas de auditoría.
El script `scripts/s09-postgresql-smoke.ps1` mantiene el flujo antes/después de
reinicio y ahora requiere `SMOKE_JWT` con un token GERENTE obtenido por login.
El script no guarda el token en su manifest. Renovarlo si expira.

JaCoCo genera el reporte al ejecutar `mvn clean test` o `mvn clean verify`:

```text
target/site/jacoco/index.html
```

El reporte contiene instrucciones, líneas, ramas, métodos y clases. Se consulta
el resultado generado para conocer la cobertura actual; no se fija un
porcentaje permanente en este README. No se busca cubrir artificialmente
`main()`, getters/setters ni defensas contra `null` del código generado.

## Persistencia

Los datos se mantienen en PostgreSQL y sobreviven al reinicio. Se conserva
`spring.jpa.hibernate.ddl-auto=update`; no se usa H2 ni create-drop.
S10 añade la tabla `usuarios` sin borrar datos anteriores. La validación real
de Usuario en esta sesión está pendiente porque DB_PASSWORD no está disponible;
el DDL se comprueba offline y se entrega una prueba PostgreSQL opt-in.

## Diagramas y auditoría

- [Diagrama de clases del dominio](docs/diagrams/class-diagram.puml).
- [Secuencia del flujo de pedido, cocina y pago](docs/diagrams/order-flow-sequence.puml).
- [Informe de auditoría final](docs/final-audit.md).

Los diagramas se entregan como fuentes PlantUML, sin imágenes binarias ni
dependencias adicionales en Maven. Las referencias por ID se distinguen de
las asociaciones entre objetos.
