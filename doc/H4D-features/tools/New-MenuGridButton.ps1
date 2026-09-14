<#
.SYNOPSIS
  Builds a new menu-grid button .res file by splicing a name/tooltip/description into an
  existing button's icon, without needing the official Haven resource editor.

.DESCRIPTION
  See doc/H4D-features/res-file-binary-format.md for the format this implements.

.PARAMETER IconDonorRes
  Path to an existing .res file whose "image" layer (icon) you want to reuse verbatim.

.PARAMETER CategoryDonorRes
  Path to any existing .res file that already lives in the target category (used only to read
  the correct parentPath/parentVer pair for that category, e.g. "customclient/menugrid/Bots").

.PARAMETER Category
  Category id as used in ad[1], e.g. "Bots", "Toggles", "OtherScriptsAndTools".

.PARAMETER ButtonId
  Button id as used in ad[2], e.g. "YapperBot". Also used as the output filename.

.PARAMETER ActionName
  Bold title shown at the top of the tooltip (also the "tooltip" layer text).

.PARAMETER PaginaText
  Body text shown below the bold title. Use literal `n for line breaks, `n`n for a blank line.

.PARAMETER OutDir
  Directory to write "<ButtonId>.res" into, e.g. res/customclient/menugrid/Bots.

.EXAMPLE
  ./New-MenuGridButton.ps1 `
    -IconDonorRes res/customclient/menugrid/OtherScriptsAndTools/CustomAlarmManager.res `
    -CategoryDonorRes res/customclient/menugrid/Bots/OceanScoutBot.res `
    -Category Bots -ButtonId YapperBot -ActionName "Spitsburgen Yapper Bot" `
    -PaginaText "Some description.`n`nMore text." `
    -OutDir res/customclient/menugrid/Bots
#>
param(
    [Parameter(Mandatory)] [string]$IconDonorRes,
    [Parameter(Mandatory)] [string]$CategoryDonorRes,
    [Parameter(Mandatory)] [string]$Category,
    [Parameter(Mandatory)] [string]$ButtonId,
    [Parameter(Mandatory)] [string]$ActionName,
    [Parameter(Mandatory)] [string]$PaginaText,
    [Parameter(Mandatory)] [string]$OutDir
)

$ErrorActionPreference = "Stop"

function Parse-Layers([byte[]]$bytes) {
    $sig = [System.Text.Encoding]::ASCII.GetBytes("Haven Resource 1")
    for ($i = 0; $i -lt $sig.Length; $i++) {
        if ($bytes[$i] -ne $sig[$i]) { throw "Bad signature in resource file" }
    }
    $pos = $sig.Length
    $ver = [BitConverter]::ToUInt16($bytes, $pos)
    $pos += 2
    $layers = @()
    while ($pos -lt $bytes.Length) {
        $nameStart = $pos
        while ($bytes[$pos] -ne 0) { $pos++ }
        $name = [System.Text.Encoding]::UTF8.GetString($bytes, $nameStart, $pos - $nameStart)
        $pos++
        $len = [BitConverter]::ToInt32($bytes, $pos)
        $pos += 4
        $dataStart = $pos
        $layers += [pscustomobject]@{
            Name        = $name
            TotalStart  = $nameStart
            DataStart   = $dataStart
            DataLen     = $len
            TotalLen    = ($dataStart + $len) - $nameStart
        }
        $pos += $len
    }
    return @{ Ver = $ver; Layers = $layers }
}

function StringZ([string]$s) { [System.Text.Encoding]::UTF8.GetBytes($s) + [byte]0 }
function UInt16LE([int]$n) { [BitConverter]::GetBytes([uint16]$n) }
function Int32LE([int]$n) { [BitConverter]::GetBytes([int32]$n) }
function BuildLayer([string]$name, [byte[]]$data) { (StringZ $name) + (Int32LE $data.Length) + $data }

$iconBytes = [System.IO.File]::ReadAllBytes($IconDonorRes)
$catBytes  = [System.IO.File]::ReadAllBytes($CategoryDonorRes)

$icon = Parse-Layers $iconBytes
$cat  = Parse-Layers $catBytes

$imgLayer = $icon.Layers | Where-Object { $_.Name -eq "image" }
if (-not $imgLayer) { throw "$IconDonorRes has no 'image' layer" }
$imageLayerBytes = $iconBytes[$imgLayer.TotalStart..($imgLayer.TotalStart + $imgLayer.TotalLen - 1)]

# Read the category donor's own action layer to recover parentPath/parentVer verbatim.
$catActionLayer = $cat.Layers | Where-Object { $_.Name -eq "action" }
if (-not $catActionLayer) { throw "$CategoryDonorRes has no 'action' layer" }
$p = $catActionLayer.DataStart
$parentPathEnd = $p
while ($catBytes[$parentPathEnd] -ne 0) { $parentPathEnd++ }
$parentPath = [System.Text.Encoding]::UTF8.GetString($catBytes, $p, $parentPathEnd - $p)
$parentVer = [BitConverter]::ToUInt16($catBytes, $parentPathEnd + 1)

$actionData = (StringZ $parentPath) + (UInt16LE $parentVer) `
    + (StringZ $ActionName) + (StringZ "") + (UInt16LE 0) `
    + (UInt16LE 3) + (StringZ "@") + (StringZ $Category) + (StringZ $ButtonId)
$actionLayerBytes = BuildLayer "action" $actionData

$tooltipLayerBytes = BuildLayer "tooltip" ([System.Text.Encoding]::UTF8.GetBytes($ActionName))
$paginaLayerBytes  = BuildLayer "pagina" ([System.Text.Encoding]::UTF8.GetBytes($PaginaText))

$header = [System.Text.Encoding]::ASCII.GetBytes("Haven Resource 1") + (UInt16LE $icon.Ver)
$full = $header + $actionLayerBytes + $imageLayerBytes + $tooltipLayerBytes + $paginaLayerBytes

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force $OutDir | Out-Null }
$outPath = Join-Path $OutDir "$ButtonId.res"
[System.IO.File]::WriteAllBytes($outPath, $full)

# Sanity re-parse before trusting the result.
$check = Parse-Layers $full
Write-Output "Wrote $outPath ($($full.Length) bytes). Layers:"
$check.Layers | ForEach-Object { Write-Output ("  {0} dataLen={1}" -f $_.Name, $_.DataLen) }
Write-Output "parentPath=$parentPath parentVer=$parentVer"
