# Especificación para construir manualmente el diagrama

Este documento **no es el diagrama entregable**. Dibujarlo manualmente en
draw.io, Lucidchart o Miro. No se genera PNG, SVG ni Mermaid en esta fase.

## Elementos y disposición

1. A la izquierda, un bloque **Developer** y un bloque **GitHub repository**.
2. En el centro, **GitHub Actions**, separado internamente en flujo QA y flujo
   PROD; a su derecha, **Docker Hub: danielvalero09/american-bites-api**.
3. En la derecha superior, un recinto **Azure QA** con **App Service QA**,
   **PostgreSQL QA** y **Mongo QA**.
4. En la derecha inferior, un recinto **Azure PROD** separado visualmente, con
   **App Service PROD**, **PostgreSQL PROD** y **Mongo PROD**.
5. Frente a ambos App Services, **Cliente / Swagger**. Dibujar usuarios de QA
   y PROD como elementos separados o dos conexiones rotuladas por ambiente.
6. Colocar **GitHub Environment: production / Required reviewers** entre la
   publicación de la imagen versionada y el App Service PROD.

## Flechas y etiquetas exactas

| Origen | Destino | Etiqueta |
| --- | --- | --- |
| Developer | GitHub | `git push develop/main` |
| GitHub | GitHub Actions QA | `push: develop/main` |
| GitHub Actions QA | Docker Hub | `tests + verify → Docker build/push: qa, sha-<commit>` |
| Docker Hub | Azure QA / App Service QA | `deploy QA; pull imagen qa fijada por digest` |
| Developer | GitHub | `tag vX.Y.Z + push del tag` |
| GitHub | GitHub Actions PROD | `push: v*.*.* → validar tag + Maven verify` |
| GitHub Actions PROD | Docker Hub | `Docker build/push: X.Y.Z, latest` |
| Docker Hub / GitHub Actions PROD | Environment production | `imagen versionada lista → esperar aprobación` |
| Reviewer | Environment production | `aprobación manual` |
| Environment production | Azure PROD / App Service PROD | `deploy PROD; pull imagen X.Y.Z` |
| Cliente / Swagger | App Service QA | `HTTPS` |
| Cliente / Swagger | App Service PROD | `HTTPS` |
| App Service QA | PostgreSQL QA | `JDBC/TLS` |
| App Service QA | Mongo QA | `MongoDB/TLS` |
| App Service PROD | PostgreSQL PROD | `JDBC/TLS` |
| App Service PROD | Mongo PROD | `MongoDB/TLS` |

Añadir una rama lateral de PR hacia GitHub Actions: `pull_request a
develop/main → test + verify + Docker build; sin push/deploy`.
Las flechas de Docker Hub representan distribución/pull del contenedor;
GitHub Actions inicia los deployments mediante Azure Login/Web Apps Deploy.

## Notas visuales obligatorias

- QA y PROD con colores o recintos diferentes. Sin flechas cruzadas entre
  sus bases de datos ni secretos compartidos.
- HTTPS termina en App Service; el contenedor Spring escucha HTTP 8080.
- PostgreSQL es la persistencia de negocio; Mongo es auditoría secundaria.
- Escribir `QA URL: PENDING` y `PROD URL: PENDING` hasta comprobar URLs reales.
- Marcar cloud como `PENDING MANUAL CONFIGURATION`; el diseño representa la
  arquitectura prevista, no evidencia de recursos creados.
- No incluir IDs, hosts, credenciales, JWT, passwords ni URI Mongo reales.

Guardar el diagrama realizado manualmente y sus capturas en la entrega cuando
se complete. Registrar el archivo/evidencia en [verification.md](verification.md).
