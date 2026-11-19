# Carga los datos de DEMOSTRACION (db/demo/datos-demo.sql) en una base de Plus Contable.
# Genera dos contrasenas al azar, las usa para crear las cuentas demo y las MUESTRA UNA SOLA VEZ en pantalla: no se guardan
# en ningun archivo. Cada ejecucion crea contrasenas nuevas.
#
# Uso (PowerShell, desde la raiz del repositorio; necesita psql):
#   $env:DEMO_DATABASE_URL = Read-Host "Cadena de conexion de la base (postgresql://...)"
#   .\db\demo\cargar-demo.ps1
# La cadena de conexion lleva la clave de la base: solo se pide por pantalla, no la pegue en archivos ni en el chat.
$ErrorActionPreference = 'Stop'
if (-not $env:DEMO_DATABASE_URL) { throw 'Defina DEMO_DATABASE_URL (cadena de conexion de la base, con sslmode=require si es Neon)' }
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw 'No se encontro psql (instale las herramientas de cliente de PostgreSQL)' }

function Nueva-Clave {
    $letras = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] 16
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $texto = -join ($bytes | ForEach-Object { $letras[$_ % $letras.Length] })
    "$texto-9a"   # termina con digito, minuscula y simbolo para cumplir politicas habituales
}

$env:DEMO_PW_CONTADOR = Nueva-Clave
$env:DEMO_PW_CLIENTE = Nueva-Clave
try {
    $sql = Join-Path $PSScriptRoot 'datos-demo.sql'
    psql $env:DEMO_DATABASE_URL -v ON_ERROR_STOP=1 -q -f $sql
    if ($LASTEXITCODE -ne 0) { throw "psql fallo (codigo $LASTEXITCODE): no se crearon las cuentas demo" }
    Write-Host ''
    Write-Host 'Cuentas demo (anote las contrasenas ahora: no se vuelven a mostrar):'
    Write-Host "  contador.demo  /  $env:DEMO_PW_CONTADOR"
    Write-Host "  cliente.demo   /  $env:DEMO_PW_CLIENTE"
}
finally {
    Remove-Item Env:DEMO_PW_CONTADOR, Env:DEMO_PW_CLIENTE -ErrorAction SilentlyContinue
}
