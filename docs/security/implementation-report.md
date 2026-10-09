# American Bites — Informe S10.1 Seguridad

Implementación JWT sobre los 33 endpoints existentes, más login. No se modificó
código de servicios, dominio de pedidos/cuentas, repositorios existentes ni
entidades de persistencia anteriores. RN-01 a RN-07, rutas y DTO previos se
conservaron; las únicas restricciones HTTP nuevas son las de seguridad.
No se ejecutaron commit, push, merge, rebase ni reset.

1. **Matriz completa de roles:** [role-matrix.md](role-matrix.md), 33 operaciones
   existentes, login y recursos de documentación. Preparada antes de implementar.

2. **Públicos:** GET `/api/v1/carta`; POST `/api/v1/auth/login`;
   `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`.
   La cadena permite `/api/v1/auth/**`; solo existe login. OPTIONS válido se
   atiende por CORS. No hay requisito global bearer en OpenAPI.

3. **GERENTE:** todas las operaciones administrativas de ingredientes/platos,
   POST `/api/v1/mesas` y supervisión de las operaciones de sala, pedidos,
   estados, cocina y pagos. Todas conservan validaciones de negocio.

4. **MESERO:** GET mesas y detalle, apertura/consulta de cuenta por mesa;
   GET cuentas/detalle; creación/listado por cuenta y listado general/detalle de
   pedidos; edición de ítems/bebida, confirmación, historial y estado ENTREGADO;
   registro y consulta de pagos. No crea mesas ni administra catálogo/cocina.

5. **COCINERO:** GET `/api/v1/cocina/pedidos`, GET pedido por id e historial;
   PATCH estado EN_PREPARACION/LISTO. No entrega, cobra ni edita ítems.

6. **CLIENTE:** carta y documentación públicas, login. Sin acceso a las 32
   operaciones protegidas. No se inventó propiedad de cuentas ni nuevas rutas.
   La matriz explica por qué crear mesas y consultar catálogo inactivo se
   reservan a GERENTE y por qué el endpoint de estados comparte dos roles.

7. **Dependencias agregadas:** `spring-boot-starter-security`,
   `jjwt-api:0.12.3`, `jjwt-impl:0.12.3` runtime,
   `jjwt-jackson:0.12.3` runtime, `spring-security-test` test.
   Árbol Maven: Spring Security 6.5.11 administrado por Boot 3.5.16;
   JJWT consistente en 0.12.3. No se observó conflicto; no se agregó OAuth2 Client.

8. **UsuarioEntity:** `com.restaurante.model.entity.UsuarioEntity`, tabla
   `usuarios`, id Long IDENTITY, email no nulo/único y normalizado en callbacks
   JPA, password no nulo/hash BCrypt de 60 caracteres, rol EnumType.STRING,
   activo no nulo. Password tiene JsonIgnore y validación BCrypt. No se expone
   entidad desde controllers. `RolUsuario` contiene exactamente ROLE_GERENTE,
   ROLE_MESERO, ROLE_COCINERO y ROLE_CLIENTE.

9. **UsuarioRepository:** JpaRepository con
   `Optional<UsuarioEntity> findByEmailIgnoreCase(String email)`.
   `UsuarioDetailsService` carga desde este repositorio, rechaza inexistentes e
   inactivos y usa `.authorities(rol.name())`, sin doble prefijo.

10. **BCrypt:** PasswordEncoder bean en PasswordConfig (coste predeterminado
    10). El único ingreso para almacenamiento de contraseña, bootstrap, llama
    `encode`. JPA valida que se almacene hash. Login compara con el encoder vía
    DaoAuthenticationProvider. Se controla el límite BCrypt de 72 bytes UTF-8
    tanto en bootstrap como login; no hay passwords/hashes en ResponseDTO.

11. **Primer GERENTE:** definir DB_PASSWORD y JWT_SECRET y, opcionalmente,
    BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD en el proceso antes de
    `mvn spring-boot:run`. Ver los comandos PowerShell exactos en la sección
    inicial Seguridad del [README](../../README.md). Ambas variables de bootstrap
    deben ser no vacías; solo crea si el email no existe, activo=true y
    ROLE_GERENTE. No sobrescribe ni reactiva; no imprime credenciales. Retirar
    variables de bootstrap después del primer arranque. Sin ellas no se crea
    usuario, pero se arranca normalmente si PostgreSQL/JWT están configurados.

12. **JwtUtil:** Base64 ≥32 bytes, HMAC, subject=email, claim `rol`, issuedAt,
    expiration. `generateToken`, `extractClaims`, `isValid`; verifica firma,
    expiración, campos requeridos y enum de rol. Secreto/tiempo se reciben por
    variables; en main no hay clave fija. Tiempo predeterminado 3600000 ms.

13. **JwtAuthFilter:** OncePerRequestFilter lee prefijo exacto Bearer, verifica
    claims, carga UserDetails de BD, contrasta rol actual y registra
    Authentication. Ausente/inválido no autentica y permite a la cadena producir
    401 en protegido. Usuario deshabilitado o rol cambiado invalida acceso con
    JWT previo. No autentica otra vez si ya existe Authentication. Se deshabilita
    registro servlet automático para evitar ejecutar el filtro dos veces.

14. **SecurityConfig:** Configuration, EnableWebSecurity, EnableMethodSecurity;
    cadena servlet STATELESS, CSRF deshabilitado, request cache/Basic/form login/
    logout deshabilitados, públicos explícitos y resto authenticated. JWT antes
    de UsernamePasswordAuthenticationFilter. Seguridad de métodos en controllers
    según matriz. Configuración servlet condicional para conservar el arranque
    sin servidor de las suites PostgreSQL previas.

15. **AuthController:** POST `/api/v1/auth/login`, LoginRequest email/password
    con Bean Validation, LoginResponse token/tipo/expirationMs; 200 válido,
    401 inválido/inactivo, 400 entrada inválida. Operation/ApiResponses.
    Credenciales inexistentes/incorrectas tienen el mismo error genérico.

16. **401:** SecurityErrorHandler implementa AuthenticationEntryPoint,
    ErrorResponse JSON con UNAUTHORIZED, status/path/timestamp/fieldErrors y
    WWW-Authenticate: Bearer. AuthenticationException de login también se
    traduce en GlobalExceptionHandler. Sin HTML ni stack trace en respuestas.

17. **403:** mismo SecurityErrorHandler implementa AccessDeniedHandler con
    FORBIDDEN. GlobalExceptionHandler tiene handler específico para denegaciones
    de @PreAuthorize, evitando su catch-all 500. CORS denegado también usa este
    JSON mediante DefaultCorsProcessor personalizado.

18. **CORS:** localhost:3000 y localhost:5173, métodos
    GET/POST/PUT/PATCH/DELETE/OPTIONS, Authorization/Content-Type/Accept,
    allowCredentials=false, preflight maxAge 3600. Sin origen wildcard.
    CorsFilter se ejecuta solo dentro de la cadena, sin doble registro servlet.

19. **Headers:** X-Frame-Options DENY, X-Content-Type-Options nosniff y CSP:
    `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
    img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none';
    base-uri 'self'; frame-ancestors 'none'`.
    Los estilos inline permiten el Swagger actual. Recursos Swagger verificados
    por HTTP; renderizado real y consola CSP requieren prueba manual.

20. **Swagger bearerAuth:** HTTP/bearer/JWT en OpenApiConfig,
    SecurityRequirement solo en controllers protegidos. OpenAPI no exige
    bearerAuth en login ni carta. Se comprobaron documentos, configuración,
    index y swagger-initializer; botón Authorize pendiente de captura visual.

21. **OWASP:** [owasp-checklist.md](owasp-checklist.md), las diez categorías
    solicitadas con APLICA/NO APLICA, riesgo, mitigación y pendiente. No se afirma
    ausencia de CVE ni validación PostgreSQL real. No se instalaron escáneres.
    INFO login exitoso solo con email; WARN login fallido, token inválido y
    acceso denegado. Password/hash/JWT/secret no se incluyen en logs nuevos.

22. **Pruebas nuevas:** 102 ejecuciones normales, cinco suites:

    | Suite | Ejecuciones | Evidencia |
    | --- | ---: | --- |
    | SecurityHttpTest | 86 | Login, 32 endpoints sin token, CLIENTE denegado en 32, JWT inválido/firma/expiración, inactivos, rol cambiado, casos positivos GERENTE/MESERO/COCINERO, RN, carta, Swagger, CORS y headers. |
    | AdminBootstrapTest | 9 | Variables ausentes, hash BCrypt, roles, idempotencia, no sobrescribir/reactivar, límite de bytes y secreto omitido en representaciones. |
    | JwtUtilTest | 4 | Configuración, claims, roles exactos y tokens inválidos. |
    | JwtAuthFilterTest | 1 | Contexto existente no se autentica dos veces. |
    | UsuarioEntityValidationTest | 2 | Rechazo de password en claro y normalización JPA de email. |

    Se añade además **S10UsuarioPostgresIT (1 opt-in)**: usuario BCrypt real,
    bootstrap repetido, reinicio y desactivación. No ejecutada sin DB_PASSWORD.

23. **Pruebas existentes adaptadas:** CartaHttpTest (20), MesaCuentaHttpTest
    (17), PedidoHttpTest (18), PagoHttpTest (6) y
    AmericanBitesHttpIntegrationTest (2) conservan escenarios/asserts y ahora
    pasan por SecurityFilterChain y proxies de métodos como GERENTE, usando
    `SecurityHttpTestSupport`. GlobalExceptionHandlerTest (7) usa WithMockUser y
    configuración real. RestauranteApplicationTests añade mock de UsuarioRepository.
    PostgreSqlSchemaTest añade Usuario al DDL y valida sus columnas/enum. No se
    deshabilitan filtros ni tests. Services/domain/mapper mantienen sus pruebas.

24. **Total:** 326 normales = 224 anteriores + 102 nuevas, sin fallos/errores/
    omisiones en clean test. Se conservaron todos los nombres/casos originales:
    [baseline de 224 casos](test-baseline.json),
    [métricas y comparación automática](verification-metrics.json).
    Las 9 S09RegressionPostgresIT originales siguen intactas; la nueva opt-in
    lleva el inventario PostgreSQL a 10, aparte del total normal. No ejecutadas
    en esta sesión por ausencia de DB_PASSWORD.

25. **JaCoCo clean test:** instrucciones 90.79%, líneas 87.87%, ramas 69.91%,
    métodos 95.22%, clases 96.05%, complejidad 82.60%. Reporte generado en
    `target/site/jacoco/index.html`; contadores completos en verification-metrics.json.

26. **mvn clean test:** BUILD SUCCESS; 326 tests, 0 failures, 0 errors, 0 skipped.
    Se requirió ejecutar Maven fuera del sandbox para usar su repositorio local
    (dentro del sandbox intentaba crear C:\.m2\repository y recibió acceso denegado).

27. **mvn clean verify:** BUILD SUCCESS; 326 tests, 0 failures, 0 errors,
    0 skipped. Genera `target/restaurante-0.0.1-SNAPSHOT.jar`. JaCoCo conserva
    los mismos porcentajes de clean test; la comparación final encontró 0
    casos previos desaparecidos. Ver contadores en verification-metrics.json.

28. **git diff --check:** aprobado, exit 0, sin errores de whitespace.
    Git avisa de conversión futura LF/CRLF propia del entorno Windows; no son
    errores de diff. Se validó además sintaxis del script PowerShell modificado.

29. **Archivos creados:** inventario completo en
    [git-status-short.txt](git-status-short.txt), entradas `??` (27 archivos):
    docs/security/{role-matrix.md,owasp-checklist.md,implementation-report.md,
    test-baseline.json,verification-metrics.json,git-status-short.txt};
    config/{PasswordConfig,SecurityConfig}; controller/AuthController;
    model/domain/enums/RolUsuario; model/entity/UsuarioEntity;
    model/dto/request/LoginRequest; model/dto/response/LoginResponse;
    repository/UsuarioRepository; security/{AdminBootstrap,JwtUtil,JwtAuthFilter,
    SecurityErrorHandler,UsuarioDetailsService}; cinco suites de seguridad;
    persistence/S10UsuarioPostgresIT; support/SecurityHttpTestSupport;
    src/test/resources/application.properties. Las clases mencionadas usan `.java`.

30. **Archivos modificados:** 23: .gitignore, README.md, pom.xml,
    scripts/s09-postgresql-smoke.ps1; OpenApiConfig; los 8 controllers previos;
    GlobalExceptionHandler; main application.properties; RestauranteApplicationTests;
    los 4 tests HTTP de controller; GlobalExceptionHandlerTest;
    AmericanBitesHttpIntegrationTest; PostgreSqlSchemaTest.

31. **git status --short:** [salida final completa](git-status-short.txt).
    Todos los cambios permanecen en working tree; no se hizo commit ni push.

32. **Prueba manual pendiente:** DB_PASSWORD no estuvo disponible, por tanto
    no se verificó Usuario en PostgreSQL real. Ejecutar opt-in en BD dedicada,
    arrancar con variables, verificar bootstrap sin duplicado y reinicio/login,
    hash BCrypt y desactivación. Añadir capturas de login, 401, 403, Authorize,
    headers y hash en BD como indica README. Validar Swagger en navegador bajo
    CSP. El script S09 requiere SMOKE_JWT de GERENTE y solo se comprobó su
    sintaxis en esta sesión. HTTPS/SSL, OAuth2 Google, MongoDB, Docker, CI/CD,
    rate limiting y demás exclusiones permanecen sin implementar.

Referencias de implementación consultadas:
[arquitectura Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html),
[JJWT oficial](https://github.com/jwtk/jjwt),
[DefaultCorsProcessor 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/cors/DefaultCorsProcessor.html).
