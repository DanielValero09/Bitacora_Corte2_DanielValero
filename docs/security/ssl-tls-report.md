# S10 — Cierre de configuración SSL/TLS local

## 1. Configuración SSL implementada

`src/main/resources/application.properties` conserva HTTP en el puerto
predeterminado 8080 y añade:

```properties
server.ssl.enabled=${SSL_ENABLED:false}
spring.config.import=optional:classpath:application-ssl-${SSL_ENABLED:false}.properties
```

Con `SSL_ENABLED=true` (literal en minúsculas) Spring Boot importa
`application-ssl-true.properties`:

```properties
server.port=8443
server.ssl.key-store=classpath:restaurante.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=restaurante
```

Sin la variable o con `false`, el archivo SSL no se carga y no se necesita
certificado ni password SSL. No se cambió `SecurityConfig` ni se añadió
`requiresChannel`: JWT, roles, 401, 403, CORS, Swagger y headers conservan su
implementación anterior.

Se comprobó con el cargador ConfigData y el Binder de Spring Boot, sin abrir
un servidor ni generar certificados: SSL omitido, `false`, `true`, contraseña
SSL obligatoria al activarlo y configuración de tests deshabilitada incluso
con `SSL_ENABLED=true`. Los cinco escenarios aprobaron. El JAR generado incluye
la configuración SSL y no contiene `.p12` ni `.env`.

Referencia: [configuración externa, importaciones y placeholders de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

## 2. Archivos modificados en esta subetapa

- `src/main/resources/application.properties`: activación opcional e importación SSL.
- `src/main/resources/application-ssl-true.properties`: nuevo archivo para HTTPS.
- `src/test/resources/application.properties`: SSL deshabilitado y puerto HTTP 8080.
- `README.md`: instrucciones SSL/TLS en Seguridad y aclaración del arranque por defecto.
- `docs/security/ssl-tls-report.md`: este reporte.

Los demás cambios del working tree ya existían al comenzar esta subetapa.

## 3. Cambios en .gitignore

Ninguno en esta subetapa: ya contenía `.env` y `*.p12`.
`git check-ignore -v src/main/resources/restaurante.p12 .env` confirmó ambas
exclusiones. No hay certificados ni `.env` versionados. El estado `M` de
`.gitignore` pertenece al trabajo anterior, sin commit.

## 4. Comando exacto para generar restaurante.p12 manualmente

Ejecutar desde la raíz del proyecto en PowerShell, con `keytool` del JDK en
`PATH`. Este comando se documentó y **no se ejecutó**:

```powershell
keytool -genkeypair -alias restaurante -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore 'src/main/resources/restaurante.p12' -validity 365 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1'
```

El comando solicita y confirma la contraseña interactivamente. No incluye
contraseñas en argumentos. Alias `restaurante`, RSA 2048, PKCS12, 365 días y
CN=localhost; SAN permite identificar localhost y 127.0.0.1.
Referencia: [keytool del JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html).

## 5. Variables necesarias

| Variable | Uso |
| --- | --- |
| `SSL_ENABLED` | `true` activa HTTPS; ausente o `false` conserva HTTP. |
| `SSL_KEYSTORE_PASSWORD` | Obligatoria con HTTPS; misma contraseña usada al crear el PKCS12. Sin valor predeterminado. |
| `DB_PASSWORD` | Variable existente para PostgreSQL. |
| `JWT_SECRET` | Variable existente para firmar JWT. |

No se incluyen valores reales. PostgreSQL debe seguir disponible como antes.

## 6. Instrucciones para arrancar HTTPS

Después de crear manualmente el certificado y disponer de las variables de
BD/JWT, ejecutar en la misma sesión PowerShell:

```powershell
$env:SSL_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password del keystore (la misma usada en keytool)' -AsSecureString)).Password
$env:SSL_ENABLED = 'true'
mvn spring-boot:run
```

Para regresar a HTTP, detener con `Ctrl+C`, definir `SSL_ENABLED=false`, retirar
`SSL_KEYSTORE_PASSWORD` de la sesión y volver a arrancar. `.env` no se carga
automáticamente por Spring Boot.

## 7. Instrucciones para probar Swagger HTTPS

Abrir [https://localhost:8443/swagger-ui/index.html](https://localhost:8443/swagger-ui/index.html).
El navegador mostrará una advertencia porque el certificado es autofirmado;
aceptar la excepción para esta prueba local. Ejecutar login y usar el token en
**Authorize → bearerAuth**. Probar una operación protegida sin token (401), con
rol insuficiente (403) y con rol autorizado.

Esta sesión verifica configuración y regresión automatizada. El arranque TLS
y Swagger por HTTPS quedan para la comprobación manual con el certificado que
creará el usuario; no se afirma haber realizado un handshake TLS real.

## 8. Total de pruebas

**326 pruebas normales aprobadas** en ambas ejecuciones; 0 fallos, 0 errores,
0 omitidas. Se conservan las 102 pruebas de las cinco suites de seguridad,
incluidos los 86 escenarios de `SecurityHttpTest`. No se añadió un certificado
a `src/test` ni se ejecutaron las suites PostgreSQL opt-in.

## 9. mvn clean test

`BUILD SUCCESS`, salida 0. Resumen:

```text
Tests run: 326, Failures: 0, Errors: 0, Skipped: 0
```

Log local ignorado: `s10-ssl-clean-test.log`. La primera ejecución restringida
no pudo acceder al repositorio Maven; se repitió con acceso autorizado y aprobó.

## 10. mvn clean verify

`BUILD SUCCESS`, salida 0. Mismo resumen de 326 pruebas aprobadas; se generó
`target/restaurante-0.0.1-SNAPSHOT.jar` y el reporte JaCoCo.
Log local ignorado: `s10-ssl-clean-verify.log`.

## 11. git diff --check

Aprobado, salida 0, sin errores de whitespace. Git emitió avisos informativos
de normalización LF/CRLF en archivos del working tree.

## 12. git status --short

Salida final; incluye los cambios previos a SSL/TLS:

```text
 M .gitignore
 M README.md
 M pom.xml
 M scripts/s09-postgresql-smoke.ps1
 M src/main/java/com/restaurante/config/OpenApiConfig.java
 M src/main/java/com/restaurante/controller/CartaController.java
 M src/main/java/com/restaurante/controller/CocinaController.java
 M src/main/java/com/restaurante/controller/CuentaController.java
 M src/main/java/com/restaurante/controller/IngredienteController.java
 M src/main/java/com/restaurante/controller/MesaController.java
 M src/main/java/com/restaurante/controller/PagoController.java
 M src/main/java/com/restaurante/controller/PedidoController.java
 M src/main/java/com/restaurante/controller/PlatoController.java
 M src/main/java/com/restaurante/exception/GlobalExceptionHandler.java
 M src/main/resources/application.properties
 M src/test/java/com/restaurante/RestauranteApplicationTests.java
 M src/test/java/com/restaurante/controller/CartaHttpTest.java
 M src/test/java/com/restaurante/controller/MesaCuentaHttpTest.java
 M src/test/java/com/restaurante/controller/PagoHttpTest.java
 M src/test/java/com/restaurante/controller/PedidoHttpTest.java
 M src/test/java/com/restaurante/exception/GlobalExceptionHandlerTest.java
 M src/test/java/com/restaurante/integration/AmericanBitesHttpIntegrationTest.java
 M src/test/java/com/restaurante/persistence/PostgreSqlSchemaTest.java
?? docs/security/
?? src/main/java/com/restaurante/config/PasswordConfig.java
?? src/main/java/com/restaurante/config/SecurityConfig.java
?? src/main/java/com/restaurante/controller/AuthController.java
?? src/main/java/com/restaurante/model/domain/enums/RolUsuario.java
?? src/main/java/com/restaurante/model/dto/request/LoginRequest.java
?? src/main/java/com/restaurante/model/dto/response/LoginResponse.java
?? src/main/java/com/restaurante/model/entity/UsuarioEntity.java
?? src/main/java/com/restaurante/repository/UsuarioRepository.java
?? src/main/java/com/restaurante/security/
?? src/main/resources/application-ssl-true.properties
?? src/test/java/com/restaurante/persistence/S10UsuarioPostgresIT.java
?? src/test/java/com/restaurante/security/
?? src/test/java/com/restaurante/support/SecurityHttpTestSupport.java
?? src/test/resources/
```

No se generaron certificados ni credenciales reales. No se hizo commit ni push.
No se implementaron MongoDB, Docker, CI/CD, OAuth2 ni Kubernetes.
