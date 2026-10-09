# S09.1: inventario de métodos y comportamiento

Comparación de `HEAD` (aa391a1) contra los fuentes auditados. El JSON `s09-1-surefire-before-audit.json` conserva las 171 invocaciones del 29/09 antes de limpiar target; `s09-1-test-method-inventory.json` conserva todos los métodos anteriores y sus nombres actuales. Un nombre eliminado no equivale automáticamente a una regla eliminada. Las pruebas PostgreSQL están preparadas, compiladas y pendientes de ejecución.

## CuentaServiceImplTest.java

| Método anterior que desaparece con ese nombre | Evidencia equivalente actual / ajuste válido |
| --- | --- |
| `abrirCuentaInicializaTodosLosDatosControladosPorElServidor` | abrirCuentaBloqueaMesaEInicializaDatos |
| `mesaInexistenteAlAbrirPropagaExcepcionYNoGuardaCuenta` | mesaInexistenteImpideAbrir |
| `segundaCuentaAbiertaParaMismaMesaLanzaExcepcion` | segundaCuentaAbiertaEsConflicto |
| `listarInicialmenteVacio` | listarVacio (Cuenta); consultasNotFoundYListaVacia (Pago); listasVaciasYFiltradoPorCuenta (Pedido); mismo nombre (Mesa) |
| `listarContieneCuentasOrdenadasPorId` | listarMapeaElOrdenSolicitadoAlRepository |
| `obtieneCuentaAbiertaPorMesa` | obtieneCuentaAbiertaConQueryDerivada |
| `permiteNuevaCuentaCuandoLaAnteriorYaNoEstaAbierta` | permiteReaperturaCuandoNoHayCuentaAbierta; pagoCierraYPermiteReapertura |
| `aperturaConcurrenteSoloCreaUnaCuentaAbierta` | S09RegressionPostgresIT.aperturaConcurrenteSoloCreaUnaCuentaAbierta — manual pendiente |

Métodos que conservan su nombre: `obtenerCuentaExistente`, `cuentaInexistenteLanzaExcepcion`, `mesaSinCuentaAbiertaLanzaExcepcion`, `mesaInexistenteAlConsultarCuentaAbiertaPropagaExcepcion`.

## MesaServiceImplTest.java

| Método anterior que desaparece con ese nombre | Evidencia equivalente actual / ajuste válido |
| --- | --- |
| `crearGeneraIdYConservaNumero` | crearDelegaIdALaBaseDeDatos |
| `idsSonDiferentesEIncrementales` | IDENTITY en PostgreSqlSchemaTest; generación real en flujoCompletoSnapshotHistorialYReaperturaSobrevivenReinicio — pendiente; no exigir consecutividad |
| `numeroDuplicadoLanzaExcepcion` | numeroDuplicadoLanzaExcepcionSinGuardar; restriccionUnicaConcurrenteSeTraduceAConflicto |
| `listarContieneElementosOrdenadosPorId` | listarMapeaOrdenSolicitadoAlRepository |

Métodos que conservan su nombre: `obtenerMesaExistente`, `mesaInexistenteLanzaExcepcion`, `listarInicialmenteVacio`.

## PagoIntegrationTest.java

| Método anterior que desaparece con ese nombre | Evidencia equivalente actual / ajuste válido |
| --- | --- |
| `pagoPermiteTerminarFlujoDeCocinaPeroImpideModificarContenido` | pagoPermiteTerminarCocinaPeroImpideModificarContenido |
| `pagoConcurrenteConAgregarItemConservaElTotalDeLaCuentaCerrada` | S09RegressionPostgresIT.pagoConcurrenteConAgregarItemConservaElTotalDeLaCuentaCerrada — manual pendiente |
| `pagarCierraCuentaYPermiteAbrirOtraParaLaMismaMesa` | pagoCierraYPermiteReapertura; flujoCompletoSnapshotHistorialYReaperturaSobrevivenReinicio — manual pendiente |
| `cuentaPedidoPagoConservaTotalYCerrarCuentaImpideModificarPedido` | totalDeCuentaReflejaItemsPersistidos; registrarPagoUsaTotalCongeladoYCierraCuentaConMismaFecha; pagoBloqueaContenidoPeroNoFlujoDeCocina |

Métodos que conservan su nombre: .

## PagoServiceImplTest.java

| Método anterior que desaparece con ese nombre | Evidencia equivalente actual / ajuste válido |
| --- | --- |
| `registraPagoConDatosCalculadosYCierraLaCuenta` | registrarPagoUsaTotalCongeladoYCierraCuentaConMismaFecha |
| `generaIdsConsecutivosParaPagosDeCuentasDiferentes` | idsDePagosSonGeneradosParaCuentasDiferentes; no exigir IDs sin saltos |
| `cuentaInexistenteImpideRegistrarPago` | cuentaInexistenteSeRechaza |
| `cuentaCerradaImpideRegistrarPago` | cuentaCerradaImpideRegistrarOtroPago |
| `pagoDuplicadoEsRechazado` | pagoDuplicadoSeRechaza; pagoExistenteSeDetectaAntesDeGuardar |
| `obtienePagoPorId` | pagoPuedeConsultarsePorIdYCuenta |
| `pagoPorIdInexistenteLanzaExcepcion` | consultasNotFoundYListaVacia |
| `obtienePagoPorCuenta` | pagoPuedeConsultarsePorIdYCuenta |
| `cuentaSinPagoLanzaExcepcionAlConsultarPago` | consultasNotFoundYListaVacia |
| `cuentaInexistenteLanzaExcepcionAlConsultarPago` | cuentaInexistenteSeRechaza |
| `listarInicialmenteVacio` | listarVacio (Cuenta); consultasNotFoundYListaVacia (Pago); listasVaciasYFiltradoPorCuenta (Pedido); mismo nombre (Mesa) |
| `listarDevuelvePagosOrdenadosPorId` | listarPagosConservaElOrdenDelRepository |
| `pagoUsaPrecioCongeladoAunqueCambieElPrecioActualDelPlato` | pagoConservaPrecioCongeladoAunqueCambieElPlato |
| `dosIntentosConcurrentesSoloRegistranUnPago` | S09RegressionPostgresIT.dosIntentosConcurrentesSoloRegistranUnPago — manual pendiente |

Métodos que conservan su nombre: .

## PedidoServiceImplTest.java

| Método anterior que desaparece con ese nombre | Evidencia equivalente actual / ajuste válido |
| --- | --- |
| `crearPedidoEnCuentaAbiertaInicializaDatosYConservaMismaInstancia` | crearPedidoInicializaEstadoPersistente; assertSame adaptado a datos recuperados |
| `obtenerPedidoExistenteDevuelveMismaInstancia` | obtenerPedidoPersistidoDevuelveSuEstadoActual; identidad de objeto Java no es contrato JPA |
| `obtenerPedidoInexistenteLanzaExcepcion` | pedidoYCuentaInexistentesProducenNotFound |
| `listarInicialmenteVacio` | listarVacio (Cuenta); consultasNotFoundYListaVacia (Pago); listasVaciasYFiltradoPorCuenta (Pedido); mismo nombre (Mesa) |
| `listarDevuelvePedidosOrdenados` | listarPedidosConservaOrdenAscendenteDelRepository |
| `listarPorCuentaExistenteFiltraPedidos` | listarPorCuentaNoMezclaPedidosDeOtraCuenta |
| `listarPorCuentaInexistentePropagaExcepcion` | pedidoYCuentaInexistentesProducenNotFound |
| `agregarItemCreaSnapshotCompletoDeNoCombo` | agregarItemCreaSnapshotYAcumulaCantidad |
| `platoInexistenteImpideAgregar` | platoInexistenteImpideAgregarItem |
| `platoInactivoImpideAgregar` | platoInactivoImpideAgregarItem |
| `platoConIngredienteAgotadoImpideAgregar` | ingredienteAgotadoImpideAgregarItem |
| `cuentaCerradaImpideModificarPedido` | cuentaCerradaImpideModificarContenido; pagoBloqueaContenidoPeroNoFlujoDeCocina |
| `estadoDistintoDeRecibidoImpideModificarPedido` | estadoDistintoDeRecibidoImpideAgregarItem; desdePreparacionContenidoEsInmutable |
| `productoRepetidoIncrementaCantidadYConservaSnapshotOriginal` | productoRepetidoConservaSnapshotOriginal — reforzado con combo/bebida y un solo ítem |
| `precioCongeladoYTotalNoCambianCuandoCambiaPrecioDelPlato` | precioCongeladoNoCambiaConElPlato |
| `relacionCuentaPedidoReflejaTotalTrasAgregarItem` | totalDeCuentaReflejaItemsPersistidos |
| `actualizarCantidadSoloCambiaCantidad` | actualizarEliminarYRetirarBebidaPersisten; pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion |
| `cantidadInvalidaImpideAgregarYActualizar` | cantidadInvalidaYPlatoNoDisponibleSeRechazan; cantidadInvalidaImpideActualizarItem |
| `itemInexistenteImpideActualizarEliminarYRetirarBebida` | retirarBebidaDeNoComboEItemInexistenteSeRechazan; itemInexistenteImpideEliminar; itemInexistenteImpideRetirarBebida |
| `eliminarItemLoRetiraSinEliminarPedido` | eliminarItemNoEliminaElPedido |
| `retirarBebidaDeComboEsIdempotenteYConservaPrecioCantidadYSubtotal` | retirarBebidaEsIdempotenteYNoCambiaPrecio — reforzado con cantidad |
| `retirarBebidaDeNoComboLanzaExcepcion` | retirarBebidaDeNoComboEItemInexistenteSeRechazan |
| `confirmarPedidoValidoMarcaConfirmacionConFechaSinCambiarEstadoNiHistorial` | confirmarRegistraFechaSinCambiarEstadoNiHistorial |
| `pedidoVacioNoSeConfirma` | confirmarRequiereItemsDisponiblesYNoPermiteRepeticion |
| `segundaConfirmacionLanzaExcepcionSinAlterarFechaOriginal` | segundaConfirmacionNoAlteraLaFechaOriginal |
| `platoEliminadoAntesDeConfirmarPropagaRecursoInexistente` | platoEliminadoAntesDeConfirmarPropagaNotFound |
| `cambioDePrecioNoModificaPrecioCongeladoAlConfirmar` | cambioDePrecioNoModificaSnapshotAlConfirmar |
| `tableroVacioCuandoNoHayPedidosElegibles` | tableroCocinaVacioSinPedidosElegibles |
| `tableroIncluyeConfirmadosActivosYExcluyeNoConfirmadosYEntregados` | tableroIncluyeConfirmadosActivosYExcluyeLosDemas — reforzado con RECIBIDO, EN_PREPARACION, LISTO, ENTREGADO |
| `recorridoCompletoRegistraTresCambiosConTodosLosDatos` | transicionesValidasPersistenHistorialCompletoRn07 — reforzado con campos de los tres cambios |
| `cadaTransicionValidaPuedeEjecutarseDesdeSuEstadoInicial` | transicionesValidasPersistenHistorialCompletoRn07 ejecuta las tres transiciones desde sus estados válidos |
| `recibidoAListoFallidoConservaExplicitamentePedidoIntacto` | recibidoAListoFallidoConservaPedidoIntacto |
| `pedidoNoConfirmadoNoPuedeEntrarEnPreparacion` | transicionInvalidaNoConfirmadoYUsuarioInvalidoSeRechazan |
| `usuarioResponsableInvalidoNoPermiteCambiarEstado` | transicionInvalidaNoConfirmadoYUsuarioInvalidoSeRechazan; usuarioResponsableNuloNoRegistraTransicion; usuarioResponsableMayorACienCaracteresNoRegistraTransicion |
| `confirmacionesConcurrentesSoloPermitenUnaConfirmacion` | S09RegressionPostgresIT.confirmacionesConcurrentesSoloPermitenUnaConfirmacion — manual pendiente |
| `transicionesConcurrentesDesdeRecibidoSoloRegistranUna` | S09RegressionPostgresIT.transicionesConcurrentesDesdeRecibidoSoloRegistranUna — manual pendiente |
| `agregarMismoPlatoConcurrentementeMantieneUnSoloItem` | S09RegressionPostgresIT.agregarMismoPlatoConcurrentementeMantieneUnSoloItem — manual pendiente |

Métodos que conservan su nombre: `cuentaCerradaImpideCrearPedido`, `comboIniciaConBebidaIncluida`, `cuentaCerradaImpideConfirmar`, `estadoDistintoDeRecibidoImpideConfirmar`, `productoAgotadoDespuesDeAgregarseImpideConfirmar`, `todosLosProductosDisponiblesPermitenConfirmar`, `transicionesInvalidasNoModificanEstadoNiHistorial`, `pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion`.

## Reglas recuperadas y diferencias de contrato

`pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion` se recuperó en esta auditoría. `productoAgotadoDespuesDeAgregarseImpideConfirmar` ahora prueba ingrediente agotado; se agregó `platoDesactivadoDespuesDeAgregarseImpideConfirmarSinCambiosParciales` para mantener también el caso anterior de plato inactivo. Se comprueban las fechas, estados, usuarios de los tres cambios RN-07, los campos completos del snapshot y todos los estados elegibles del tablero.

Se conserva la existencia, unicidad y generación del ID. Una secuencia PostgreSQL puede tener saltos: la consecutividad exacta del contador en memoria y `assertSame` entre llamadas independientes dejaron de ser requisitos de negocio. Las seis reglas de concurrencia anteriores se trasladaron a PostgreSQL real: apertura, pagos, confirmar, transición, mismo plato y pago contra edición. No se sustituyeron por una afirmación de que las anotaciones bastan.

`transicionesInvalidasNoModificanEstadoNiHistorial` desapareció de las 171 invocaciones y está restaurada con exactamente los nueve argumentos originales: RECIBIDO→LISTO, RECIBIDO→ENTREGADO, EN_PREPARACION→ENTREGADO, LISTO→EN_PREPARACION, ENTREGADO→RECIBIDO, ENTREGADO→EN_PREPARACION, ENTREGADO→LISTO, ENTREGADO→ENTREGADO, RECIBIDO→RECIBIDO. En los fuentes auditados se llega al estado inicial mediante transiciones válidas y se compara el historial anterior; no se simula el estado cambiando una referencia de dominio desconectada.

## Nombres del baseline que no fueron ejecutados en el reporte de 171

Esta lista usa la firma `@Test`/`@ParameterizedTest` de HEAD y los nombres efectivamente presentes en el XML del 29/09. Es una ausencia de esa invocación/nombre; las reescrituras equivalentes se trazan arriba. No existe un commit de los fuentes exactos que produjeron 171, por lo que no se atribuye una fecha ni un parche histórico que Git no conserva.

### CuentaServiceImplTest.java

- `abrirCuentaInicializaTodosLosDatosControladosPorElServidor` — @Test
- `mesaInexistenteAlAbrirPropagaExcepcionYNoGuardaCuenta` — @Test
- `segundaCuentaAbiertaParaMismaMesaLanzaExcepcion` — @Test
- `listarInicialmenteVacio` — @Test
- `listarContieneCuentasOrdenadasPorId` — @Test
- `obtieneCuentaAbiertaPorMesa` — @Test
- `mesaInexistenteAlConsultarCuentaAbiertaPropagaExcepcion` — @Test
- `permiteNuevaCuentaCuandoLaAnteriorYaNoEstaAbierta` — @Test
- `aperturaConcurrenteSoloCreaUnaCuentaAbierta` — @Test

### MesaServiceImplTest.java

- `crearGeneraIdYConservaNumero` — @Test
- `idsSonDiferentesEIncrementales` — @Test
- `numeroDuplicadoLanzaExcepcion` — @Test
- `listarContieneElementosOrdenadosPorId` — @Test

### PagoIntegrationTest.java

- `pagoPermiteTerminarFlujoDeCocinaPeroImpideModificarContenido` — @Test
- `pagoConcurrenteConAgregarItemConservaElTotalDeLaCuentaCerrada` — @Test
- `pagarCierraCuentaYPermiteAbrirOtraParaLaMismaMesa` — @Test
- `cuentaPedidoPagoConservaTotalYCerrarCuentaImpideModificarPedido` — @Test

### PagoServiceImplTest.java

- `registraPagoConDatosCalculadosYCierraLaCuenta` — @Test
- `generaIdsConsecutivosParaPagosDeCuentasDiferentes` — @Test
- `cuentaInexistenteImpideRegistrarPago` — @Test
- `cuentaCerradaImpideRegistrarPago` — @Test
- `pagoDuplicadoEsRechazado` — @Test
- `obtienePagoPorId` — @Test
- `pagoPorIdInexistenteLanzaExcepcion` — @Test
- `obtienePagoPorCuenta` — @Test
- `cuentaSinPagoLanzaExcepcionAlConsultarPago` — @Test
- `cuentaInexistenteLanzaExcepcionAlConsultarPago` — @Test
- `listarInicialmenteVacio` — @Test
- `listarDevuelvePagosOrdenadosPorId` — @Test
- `pagoUsaPrecioCongeladoAunqueCambieElPrecioActualDelPlato` — @Test
- `dosIntentosConcurrentesSoloRegistranUnPago` — @Test

### PedidoServiceImplTest.java

- `crearPedidoEnCuentaAbiertaInicializaDatosYConservaMismaInstancia` — @Test
- `obtenerPedidoExistenteDevuelveMismaInstancia` — @Test
- `obtenerPedidoInexistenteLanzaExcepcion` — @Test
- `listarInicialmenteVacio` — @Test
- `listarDevuelvePedidosOrdenados` — @Test
- `listarPorCuentaExistenteFiltraPedidos` — @Test
- `listarPorCuentaInexistentePropagaExcepcion` — @Test
- `agregarItemCreaSnapshotCompletoDeNoCombo` — @Test
- `comboIniciaConBebidaIncluida` — @Test
- `platoInexistenteImpideAgregar` — @Test
- `platoInactivoImpideAgregar` — @Test
- `platoConIngredienteAgotadoImpideAgregar` — @Test
- `cuentaCerradaImpideModificarPedido` — @Test
- `estadoDistintoDeRecibidoImpideModificarPedido` — @Test
- `productoRepetidoIncrementaCantidadYConservaSnapshotOriginal` — @Test
- `precioCongeladoYTotalNoCambianCuandoCambiaPrecioDelPlato` — @Test
- `relacionCuentaPedidoReflejaTotalTrasAgregarItem` — @Test
- `actualizarCantidadSoloCambiaCantidad` — @Test
- `cantidadInvalidaImpideAgregarYActualizar` — @Test
- `itemInexistenteImpideActualizarEliminarYRetirarBebida` — @Test
- `eliminarItemLoRetiraSinEliminarPedido` — @Test
- `retirarBebidaDeComboEsIdempotenteYConservaPrecioCantidadYSubtotal` — @Test
- `retirarBebidaDeNoComboLanzaExcepcion` — @Test
- `confirmarPedidoValidoMarcaConfirmacionConFechaSinCambiarEstadoNiHistorial` — @Test
- `pedidoVacioNoSeConfirma` — @Test
- `segundaConfirmacionLanzaExcepcionSinAlterarFechaOriginal` — @Test
- `cuentaCerradaImpideConfirmar` — @Test
- `estadoDistintoDeRecibidoImpideConfirmar` — @Test
- `productoAgotadoDespuesDeAgregarseImpideConfirmar` — @Test
- `platoEliminadoAntesDeConfirmarPropagaRecursoInexistente` — @Test
- `cambioDePrecioNoModificaPrecioCongeladoAlConfirmar` — @Test
- `todosLosProductosDisponiblesPermitenConfirmar` — @Test
- `tableroVacioCuandoNoHayPedidosElegibles` — @Test
- `tableroIncluyeConfirmadosActivosYExcluyeNoConfirmadosYEntregados` — @Test
- `recorridoCompletoRegistraTresCambiosConTodosLosDatos` — @Test
- `cadaTransicionValidaPuedeEjecutarseDesdeSuEstadoInicial` — @Test
- `transicionesInvalidasNoModificanEstadoNiHistorial` — @ParameterizedTest, 9 invocaciones
- `recibidoAListoFallidoConservaExplicitamentePedidoIntacto` — @Test
- `pedidoNoConfirmadoNoPuedeEntrarEnPreparacion` — @Test
- `usuarioResponsableInvalidoNoPermiteCambiarEstado` — @Test
- `pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion` — @Test
- `confirmacionesConcurrentesSoloPermitenUnaConfirmacion` — @Test
- `transicionesConcurrentesDesdeRecibidoSoloRegistranUna` — @Test
- `agregarMismoPlatoConcurrentementeMantieneUnSoloItem` — @Test
