# S10.1 — Checklist OWASP

Evaluación de código y pruebas disponibles, 2026-10-01. APLICA significa que la
categoría es relevante al proyecto, no que exista una vulnerabilidad explotada.
NO APLICA se limita al alcance actual auditado. No se instalaron escáneres ni
se ejecutó un análisis de CVE; este documento no certifica ausencia de fallos.

| Categoría | Evaluación | Riesgo encontrado | Mitigación implementada o constatada | Pendiente real |
| --- | --- | --- | --- | --- |
| Broken Access Control | APLICA | Las 33 operaciones estaban abiertas; clientes podían administrar y consultar cuentas ajenas. | Cadena JWT stateless, cierre por defecto, matriz de roles y `@PreAuthorize`. CLIENTE recibe 403 en las 32 operaciones protegidas; cada una devuelve 401 sin token. Cocina y sala separan estados destino. | No hay relación Usuario–Cuenta; no se habilitó acceso de CLIENTE a cuentas. Si se añade cuenta propia, exige autorización por propietario. Las rutas futuras necesitan matriz y pruebas. |
| Cryptographic Failures | APLICA | Credenciales nuevas y tokens necesitan almacenamiento/firma seguros; HTTP local no cifra transporte. | BCrypt (coste predeterminado 10), validación de hash en UsuarioEntity, máximo 72 bytes para evitar truncamiento; HMAC con clave Base64 ≥32 bytes, firma y expiración verificadas. Secret y contraseña BD por entorno; `.env`/`*.p12` ignorados. | HTTPS y rotación de claves quedan pendientes de su subetapa. Reutilizar el secreto entre reinicios; cambiarlo invalida tokens. BD real no verificada en esta sesión. |
| Injection | APLICA | Entradas HTTP y consultas pueden recibir datos maliciosos. | Bean Validation; consultas JPA y SQL de pruebas parametrizadas revisadas. Login no construye SQL. Roles son enum validado. No se detectó concatenación de entrada en SQL productivo. | No se hizo una prueba de penetración; mantener parámetros en consultas futuras. |
| Insecure Design | APLICA | Riesgo de mezclar roles, eludir RN y confiar en `usuarioResponsable` recibido del cliente. | Permisos por endpoint/estado sin modificar RN-01 a RN-07. Pruebas prueban rechazo de edición en preparación y transiciones inválidas. Bootstrap no sobrescribe usuarios ni roles. | `usuarioResponsable` sigue siendo un dato del contrato existente, no una identidad verificada del actor; no se cambió RN-07 ni el DTO. Una futura mejora de auditoría debería vincular el actor autenticado sin romper el contrato. |
| Security Misconfiguration | APLICA | API abierta, errores de seguridad HTML y CORS demasiado amplio. | JSON ErrorResponse 401/403 en filtros y MVC; sin Basic/form login ni sesión; CSRF deshabilitado para Bearer en header. CORS únicamente localhost:3000/5173, sin cookies. DENY, nosniff y CSP compatible con recursos locales Swagger. | Validación visual de Swagger/CSP en navegador y definición de orígenes/HTTPS de despliegue pendientes. `show-sql=true` se conserva del entorno previo: revisar antes de producción; no se habilita logging de parámetros. |
| Vulnerable/Outdated Components | APLICA | Dependencias pueden tener CVE; JJWT 0.12.3 es la versión solicitada. | Versiones explícitas y BOM de Boot; árbol Maven revisado: Security 6.5.11, JJWT api/impl/jackson 0.12.3, sin conflicto observado; compila. | No hay auditoría de vulnerabilidades automatizada ni garantía de ausencia de CVE. Revisar avisos de proveedores antes de despliegue; no se improvisó otra versión. |
| Identification/Authentication Failures | APLICA | API sin login; enumeración de usuarios, tokens inválidos o usuarios deshabilitados. | Login con mensaje genérico, UserDetails por email, BCrypt, JWT firmado y expirado, usuario activo y rol actual consultados en BD por petición. Pruebas para inexistente/password incorrecto/inactivo/firma/expiración/rol cambiado. | Rate limiting, MFA y refresh/revocación individual no se implementan en S10.1. Tokens son de una hora por defecto; logout del cliente elimina su copia. |
| Software/Data Integrity | APLICA | Un token alterado o dependencia sustituida puede cambiar identidad/comportamiento. | Firma JWT y comprobación de rol contra Usuario; enum JPA y restricción unique de email. Dependencias resueltas mediante Maven y compilación/pruebas. | No se verificaron firmas de artefactos ni cadena de suministro completa. CI/CD está fuera de esta etapa. DDL de usuarios verificado offline; restricciones en PostgreSQL real pendientes. |
| Security Logging/Monitoring | APLICA | Falta de eventos de login/denegación; exposición de secretos en registros. | INFO login exitoso solo con email; WARN fallo de login, acceso denegado y token inválido sin credenciales/token/secret. DTO de login omite secretos en `toString`; password de entidad tiene JsonIgnore. | No hay alertas centralizadas ni política de retención. Revisar el manejo existente de errores inesperados antes de producción; no se enviaron logs ni datos a servicios externos. |
| SSRF | NO APLICA | No se encontraron endpoints que descarguen URLs suministradas por usuarios ni clientes HTTP de salida en el código actual. | No se añade comunicación HTTP de salida para JWT; consulta local de repositorios. | Reevaluar si se incorporan URLs remotas, webhooks o integraciones. OAuth2 Google aún no se implementa. |

## Evidencia y límites

- [Matriz completa](role-matrix.md), [informe de implementación](implementation-report.md).
- `SecurityHttpTest`: login, 32 rechazos 401, 32 rechazos de CLIENTE 403,
  escenarios positivos con roles, RN, CORS, headers y recursos/OpenAPI públicos.
- `AdminBootstrapTest`, `UsuarioEntityValidationTest`, `JwtUtilTest` y
  `JwtAuthFilterTest`: BCrypt, idempotencia, protección de secretos, claims y
  ausencia de doble autenticación.
- `PostgreSqlSchemaTest` genera DDL PostgreSQL con Hibernate real sin conexión;
  esto no demuestra persistencia real. `S10UsuarioPostgresIT` comprueba
  BCrypt, restart y desactivación solo cuando se habilita con credenciales reales.
- Sin DB_PASSWORD en el proceso no se ejecuta PostgreSQL real ni se declara
  verificada la persistencia de Usuario. Se conservan las 9 pruebas S09 opt-in.

Referencias técnicas: [arquitectura de seguridad Spring](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
y [documentación oficial JJWT](https://github.com/jwtk/jjwt).
