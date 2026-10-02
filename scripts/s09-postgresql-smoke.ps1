param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('BeforeRestart', 'AfterRestart')]
    [string]$Stage,
    [string]$BaseUrl = 'http://localhost:8080/api/v1'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'DB_PASSWORD no está disponible. No se ejecuta la prueba PostgreSQL.'
}
$manifestPath = Join-Path $PSScriptRoot '../target/s09-postgresql-smoke.json'
function Invoke-Api([string]$Method, [string]$Route, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl/$Route" }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
    }
    Invoke-RestMethod @parameters
}
function Assert-Rule([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Assert-References($Ids) {
    Assert-Rule ($null -ne $Ids) 'No hay referencias de BeforeRestart'
    Assert-Rule ($Ids.version -eq 1 -and $Ids.baseUrl -eq $BaseUrl) 'Manifest incompatible o de otra API; ejecute BeforeRestart'
    foreach ($field in @('mesaId', 'cuentaId', 'pedidoId', 'platoId', 'itemId', 'pagoId')) {
        $value = 0L
        Assert-Rule ([long]::TryParse([string]$Ids.$field, [ref]$value) -and $value -gt 0) "Referencia invalida: $field"
    }
    Assert-Rule (-not [string]::IsNullOrWhiteSpace($Ids.runId) -and
        $Ids.nombrePlato -eq "Auditoria-$($Ids.runId)" -and $Ids.numeroMesa -gt 0) 'Datos de referencia incompletos'
}
function New-SmokeMesa {
    # Un numero duplicado (HTTP 409) no impide probar una BD con datos previos.
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        try {
            return (Invoke-Api POST mesas @{ numero = (Get-Random -Minimum 100000 -Maximum 1000000000) })
        } catch {
            if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
        }
    }
    throw 'No se pudo crear una mesa con numero unico tras 10 intentos'
}
function Assert-Persisted($Ids) {
    Assert-References $Ids
    $mesa = Invoke-Api GET "mesas/$($Ids.mesaId)"
    $cuenta = Invoke-Api GET "cuentas/$($Ids.cuentaId)"
    $pedido = Invoke-Api GET "pedidos/$($Ids.pedidoId)"
    $pago = Invoke-Api GET "cuentas/$($Ids.cuentaId)/pago"
    $plato = Invoke-Api GET "platos/$($Ids.platoId)"
    # Invoke-RestMethod emite el array JSON como un solo objeto al pipeline.
    # Capturarlo primero evita contar el contenedor en @(Invoke-Api ...).
    $historialResponse = Invoke-Api GET "pedidos/$($Ids.pedidoId)/historial"
    Assert-Rule ($historialResponse -is [array]) 'Contrato de historial inesperado: se esperaba un array JSON directo'
    $historial = @($historialResponse)
    Write-Host "Historial recuperado: $($historial.Count)"
    Assert-Rule ($mesa.id -eq $Ids.mesaId -and $mesa.numero -eq $Ids.numeroMesa) 'Mesa no persistida'
    Assert-Rule ($cuenta.id -eq $Ids.cuentaId -and $cuenta.mesaId -eq $Ids.mesaId) 'Referencias de cuenta incorrectas'
    Assert-Rule ($pedido.id -eq $Ids.pedidoId -and $pedido.cuentaId -eq $Ids.cuentaId) 'Referencias de pedido incorrectas'
    Assert-Rule ($pago.cuentaId -eq $Ids.cuentaId -and $plato.id -eq $Ids.platoId -and
        $plato.nombre -eq $Ids.nombrePlato) 'Referencias de pago/plato incorrectas'
    Assert-Rule ($cuenta.estado -eq 'CERRADA') 'Cuenta no cerrada'
    Assert-Rule ($cuenta.fechaCierre -eq $pago.fechaHora) 'fechaCierre != fechaHora del pago'
    Assert-Rule ($pago.id -eq $Ids.pagoId) 'Pago no persistido'
    Assert-Rule ([decimal]$pago.monto -eq 20000) 'Pago usó precio actual'
    Assert-Rule ([decimal]$plato.precio -eq 30000) 'Plato actualizado no persistido'
    Assert-Rule ($pedido.estado -eq 'ENTREGADO' -and $pedido.confirmado) 'Pedido no persistido'
    Assert-Rule ($pedido.items.Count -eq 1) 'Items no persistidos'
    $item = $pedido.items[0]
    Assert-Rule ($item.id -eq $Ids.itemId -and $item.platoId -eq $Ids.platoId) 'IDs del snapshot incorrectos'
    Assert-Rule ($item.nombrePlato -eq $Ids.nombrePlato) 'Nombre del snapshot incorrecto'
    Assert-Rule ([decimal]$item.precioCongelado -eq 20000 -and $item.cantidad -eq 1) 'Precio/cantidad del snapshot incorrectos'
    Assert-Rule ($item.combo -and $item.bebidaIncluida) 'Combo/bebida no persistidos'
    Assert-Rule ($historial.Count -eq 3) "RN-07 esperaba exactamente 3 cambios; recuperados: $($historial.Count)"
    $anteriores = @('RECIBIDO', 'EN_PREPARACION', 'LISTO')
    $nuevos = @('EN_PREPARACION', 'LISTO', 'ENTREGADO')
    $usuarios = @('auditoria-cocina-1', 'auditoria-cocina-2', 'auditoria-mesero')
    for ($i = 0; $i -lt 3; $i++) {
        Write-Host "$($i + 1). $($historial[$i].estadoAnterior) -> $($historial[$i].estadoNuevo)"
        Assert-Rule ($historial[$i].estadoAnterior -eq $anteriores[$i] -and
            $historial[$i].estadoNuevo -eq $nuevos[$i] -and
            $historial[$i].usuarioResponsable -eq $usuarios[$i] -and
            -not [string]::IsNullOrWhiteSpace($historial[$i].fechaHora)) "Historial incompleto o desordenado en cambio $($i + 1): esperado $($anteriores[$i]) -> $($nuevos[$i])"
    }
    Assert-Rule ([decimal]$cuenta.total -eq 20000 -and [decimal]$pedido.total -eq 20000) 'Totales no respetan snapshot'
}

if ($Stage -eq 'BeforeRestart') {
    $runId = [guid]::NewGuid().ToString()
    $mesa = New-SmokeMesa
    $cuenta = Invoke-Api POST "mesas/$($mesa.id)/cuentas"
    $pedido = Invoke-Api POST "cuentas/$($cuenta.id)/pedidos"
    $pedidoId = $pedido.id
    Write-Host "Pedido creado: $pedidoId"
    # El plato está persistido antes de agregarse al pedido.
    $plato = Invoke-Api POST platos @{
        nombre = "Auditoria-$runId"; descripcion = 'S09.1'
        precio = 20000; combo = $true; ingredienteIds = @()
    }
    $pedido = Invoke-Api POST "pedidos/$pedidoId/items" @{ platoId = $plato.id; cantidad = 1 }
    Assert-Rule ($pedido.id -eq $pedidoId -and $pedido.items.Count -eq 1) 'Respuesta de agregar item incorrecta'
    $itemId = $pedido.items[0].id
    $null = Invoke-Api PUT "platos/$($plato.id)" @{
        nombre = $plato.nombre; descripcion = 'S09.1'; precio = 30000
        combo = $true; ingredienteIds = @()
    }
    $null = Invoke-Api POST "pedidos/$pedidoId/confirmacion"
    $null = Invoke-Api PATCH "pedidos/$pedidoId/estado" @{
        nuevoEstado = 'EN_PREPARACION'; usuarioResponsable = 'auditoria-cocina-1'
    }
    $pago = Invoke-Api POST "cuentas/$($cuenta.id)/pago"
    $null = Invoke-Api PATCH "pedidos/$pedidoId/estado" @{
        nuevoEstado = 'LISTO'; usuarioResponsable = 'auditoria-cocina-2'
    }
    $null = Invoke-Api PATCH "pedidos/$pedidoId/estado" @{
        nuevoEstado = 'ENTREGADO'; usuarioResponsable = 'auditoria-mesero'
    }
    $ids = [pscustomobject]@{
        version = 1; baseUrl = $BaseUrl; runId = $runId; numeroMesa = $mesa.numero
        mesaId = $mesa.id; cuentaId = $cuenta.id; pedidoId = $pedidoId
        platoId = $plato.id; nombrePlato = $plato.nombre; itemId = $itemId; pagoId = $pago.id
    }
    Assert-Persisted $ids
    $null = New-Item -ItemType Directory -Force -Path (Split-Path $manifestPath)
    $ids | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Write-Output 'Flujo validado. Detenga la aplicación, arránquela otra vez con la misma BD y ejecute AfterRestart.'
    $ids
} else {
    Assert-Rule (Test-Path -LiteralPath $manifestPath -PathType Leaf) 'Falta el manifest; ejecute BeforeRestart primero'
    $ids = Get-Content -Raw -LiteralPath $manifestPath -Encoding UTF8 | ConvertFrom-Json
    Assert-Persisted $ids
    $nueva = Invoke-Api POST "mesas/$($ids.mesaId)/cuentas"
    Assert-Rule ($nueva.id -gt 0 -and $nueva.id -ne $ids.cuentaId -and
        $nueva.mesaId -eq $ids.mesaId -and $nueva.estado -eq 'ABIERTA') 'No se pudo abrir una nueva cuenta'
    Write-Output 'Persistencia tras reinicio y reapertura verificadas.'
    $nueva
}
