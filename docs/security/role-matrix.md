# S10.1 — Matriz real de roles

Auditoría previa: 8 controllers, 33 operaciones de negocio existentes. S10.1
añade únicamente `POST /api/v1/auth/login` (34 operaciones de API en total).
Las rutas y los DTO existentes se conservan; la seguridad añade 401/403.

Cada nombre de rol de esta tabla es la authority literal de BD, JWT y
UserDetails. `hasRole('GERENTE')` compara `ROLE_GERENTE`; nunca se añade
`ROLE_` dos veces. Público permite anónimos y cualquiera de los cuatro roles.

| Método | Endpoint | Público/Protegido | Roles permitidos | Justificación |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/ingredientes` | Protegido | ROLE_GERENTE | Administración de ingredientes. |
| GET | `/api/v1/ingredientes` | Protegido | ROLE_GERENTE | Inventario administrativo; cocina dispone del tablero. |
| GET | `/api/v1/ingredientes/{id}` | Protegido | ROLE_GERENTE | Detalle administrativo. |
| PATCH | `/api/v1/ingredientes/{id}/disponibilidad` | Protegido | ROLE_GERENTE | Cambia disponibilidad compartida del catálogo. |
| POST | `/api/v1/platos` | Protegido | ROLE_GERENTE | Crea catálogo. |
| GET | `/api/v1/platos` | Protegido | ROLE_GERENTE | Incluye platos inactivos; no es la carta pública. |
| GET | `/api/v1/platos/{id}` | Protegido | ROLE_GERENTE | Puede consultar platos inactivos. |
| PUT | `/api/v1/platos/{id}` | Protegido | ROLE_GERENTE | Modifica catálogo. |
| DELETE | `/api/v1/platos/{id}` | Protegido | ROLE_GERENTE | Desactivación lógica administrativa. |
| GET | `/api/v1/carta` | Público | Todos y anónimo | Carta digital con platos activos y disponibilidad. |
| POST | `/api/v1/mesas` | Protegido | ROLE_GERENTE | Alta de mesa: configuración del restaurante. |
| GET | `/api/v1/mesas` | Protegido | ROLE_MESERO, ROLE_GERENTE | Operación de sala. |
| GET | `/api/v1/mesas/{id}` | Protegido | ROLE_MESERO, ROLE_GERENTE | Consulta de sala. |
| POST | `/api/v1/mesas/{mesaId}/cuentas` | Protegido | ROLE_MESERO, ROLE_GERENTE | Apertura de cuenta. |
| GET | `/api/v1/mesas/{mesaId}/cuenta-abierta` | Protegido | ROLE_MESERO, ROLE_GERENTE | Cuenta operativa de mesa. |
| GET | `/api/v1/cuentas` | Protegido | ROLE_MESERO, ROLE_GERENTE | Consulta de cuentas para sala y supervisión. |
| GET | `/api/v1/cuentas/{id}` | Protegido | ROLE_MESERO, ROLE_GERENTE | Consulta de cuenta y total. |
| POST | `/api/v1/cuentas/{cuentaId}/pedidos` | Protegido | ROLE_MESERO, ROLE_GERENTE | Crear pedido en cuenta abierta. |
| GET | `/api/v1/cuentas/{cuentaId}/pedidos` | Protegido | ROLE_MESERO, ROLE_GERENTE | Pedidos de una cuenta de sala. |
| GET | `/api/v1/pedidos` | Protegido | ROLE_MESERO, ROLE_GERENTE | Listado general; cocina usa su listado filtrado. |
| GET | `/api/v1/pedidos/{id}` | Protegido | ROLE_MESERO, ROLE_COCINERO, ROLE_GERENTE | Detalle necesario para atender y preparar pedidos. |
| POST | `/api/v1/pedidos/{pedidoId}/items` | Protegido | ROLE_MESERO, ROLE_GERENTE | Edición de sala, sujeta a RN-01/RN-04. |
| PATCH | `/api/v1/pedidos/{pedidoId}/items/{itemId}` | Protegido | ROLE_MESERO, ROLE_GERENTE | Cantidad, sujeta a reglas existentes. |
| DELETE | `/api/v1/pedidos/{pedidoId}/items/{itemId}` | Protegido | ROLE_MESERO, ROLE_GERENTE | Retiro, sujeto a reglas existentes. |
| PATCH | `/api/v1/pedidos/{pedidoId}/items/{itemId}/bebida` | Protegido | ROLE_MESERO, ROLE_GERENTE | Retiro de bebida sin alterar RN-03. |
| POST | `/api/v1/pedidos/{pedidoId}/confirmacion` | Protegido | ROLE_MESERO, ROLE_GERENTE | Sala confirma el pedido. |
| PATCH | `/api/v1/pedidos/{pedidoId}/estado` | Protegido | ROLE_COCINERO: EN_PREPARACION/LISTO; ROLE_MESERO: ENTREGADO; ROLE_GERENTE: cualquier solicitud sujeta a RN-06 | Un endpoint compartido; autoriza por estado solicitado sin modificar las transiciones válidas. |
| GET | `/api/v1/pedidos/{pedidoId}/historial` | Protegido | ROLE_MESERO, ROLE_COCINERO, ROLE_GERENTE | Seguimiento operativo de preparación y entrega. |
| GET | `/api/v1/cocina/pedidos` | Protegido | ROLE_COCINERO, ROLE_GERENTE | Tablero de cocina. |
| POST | `/api/v1/cuentas/{cuentaId}/pago` | Protegido | ROLE_MESERO, ROLE_GERENTE | Cobro y cierre según reglas existentes. |
| GET | `/api/v1/cuentas/{cuentaId}/pago` | Protegido | ROLE_MESERO, ROLE_GERENTE | Consulta de cobro. |
| GET | `/api/v1/pagos` | Protegido | ROLE_MESERO, ROLE_GERENTE | Consulta de pagos para sala y supervisión. |
| GET | `/api/v1/pagos/{id}` | Protegido | ROLE_MESERO, ROLE_GERENTE | Detalle de pago. |
| POST | `/api/v1/auth/login` | Público | Todos y anónimo | Autenticación por email/password; no exige JWT previo. |
| GET | `/swagger-ui.html` | Público | Todos y anónimo | Redirección de documentación. |
| GET | `/swagger-ui/**` | Público | Todos y anónimo | Interfaz y recursos Swagger. |
| GET | `/v3/api-docs/**` | Público | Todos y anónimo | OpenAPI y configuración Swagger. |

## Decisiones de mínimo privilegio

- Crear mesas es administración; consultarlas y abrir cuentas es operación de MESERO.
- Las consultas administrativas de platos pueden mostrar inactivos: se reservan a GERENTE.
- COCINERO consulta tablero, detalle e historial, pero no cuentas, pagos ni edición de ítems.
- La ruta compartida de estados exige el rol correspondiente al estado destino.
  Campos nulos siguen pasando por Bean Validation (400) para roles operativos autorizados.
- CLIENTE accede a carta y documentación públicas y puede hacer login. No existe
  relación Usuario–Cuenta ni endpoint de cuenta propia; no se habilita acceso a cuentas.
- GERENTE supervisa todas las operaciones existentes. RN-01 a RN-07 siguen
  aplicándose también al GERENTE; tener permisos nunca evita validaciones de negocio.
- La configuración permite `/api/v1/auth/**` sin token, pero solo existe login.
  Todo endpoint futuro restante requiere autenticación y debe recibir permisos
  explícitos antes de publicarse. OPTIONS de preflight se procesa mediante CORS.

## Auditoría de los contratos y pruebas

Se revisaron todos los controllers, DTO request/response, OpenApiConfig,
GlobalExceptionHandler/ErrorResponse, pom.xml, application.properties,
.gitignore, README y suites HTTP/integración. No había seguridad ni Usuario.
Los HTTP de negocio usan `standaloneSetup` con services reales y repositories
de prueba; la nueva adaptación debe incluir filtros y proxies de method security.
La suite GlobalExceptionHandler usa `@WebMvcTest`; necesita contexto autenticado.
La suite PostgreSQL opt-in arranca sin servidor HTTP y debe conservar ese modo.
El README describía memoria aunque el código usa JPA: se corrige la documentación.
