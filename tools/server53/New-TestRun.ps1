param([switch]$VerifyOnly, [string]$Output)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Kit = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function Safe([string]$Root, [string]$Name) {
    if ($Name -and ($Name -match '(^/|\\|:|(^|/)\.\.?(/|$)|//)')) { throw 'Unsafe package path' }
    $Path = [IO.Path]::GetFullPath((Join-Path $Root $Name))
    $Part = $Path
    while ($Part) {
        if (Test-Path -LiteralPath $Part) {
            $Item = Get-Item -Force -LiteralPath $Part
            if ($Item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Redirect/reparse point forbidden' }
        }
        $Part = [IO.Path]::GetDirectoryName($Part)
    }
    return $Path
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function WriteJson([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 100) + "`n"), (New-Object Text.UTF8Encoding($false)))
}
function Inventory([string]$Root) {
    $Files = @{}
    Safe $Root '' | Out-Null
    # Walk manually: reject a reparse directory before traversing it.
    $Pending = New-Object 'Collections.Generic.Stack[string]'
    $Pending.Push($Root)
    while ($Pending.Count -gt 0) {
        foreach ($Item in Get-ChildItem -LiteralPath $Pending.Pop() -Force) {
            $Name = $Item.FullName.Substring($Root.Length + 1).Replace('\','/')
            Safe $Root $Name | Out-Null
            if ($Item.PSIsContainer) { $Pending.Push($Item.FullName) }
            else { $Files[$Name] = Hash $Item.FullName }
        }
    }
    return $Files
}
function CheckInventory($Actual,$Expected) {
    $Props = @($Expected.PSObject.Properties)
    if ($Actual.Count -ne $Props.Count) { throw 'Missing or extra pinned file' }
    foreach ($P in $Props) { if (-not $Actual.ContainsKey($P.Name) -or $Actual[$P.Name] -cne $P.Value) { throw ('Changed pinned file: '+$P.Name) } }
}
try {
    $Raw = Safe $Kit 'KIT.json'
    if ((Hash $Raw) -cne ((Get-Content -Raw -Encoding UTF8 -LiteralPath (Safe $Kit 'KIT.sha256')).Split()[0])) { throw 'Kit manifest checksum mismatch' }
    $Policy = Get-Content -Raw -Encoding UTF8 -LiteralPath $Raw | ConvertFrom-Json
    if ($Policy.schema -ne 1 -or $Policy.kind -ne 'catancraft-immutable-server53-kit') { throw 'Unsupported kit schema' }
    $Baseline = Safe $Kit 'baseline'
    CheckInventory (Inventory $Baseline) $Policy.baselineFiles
    $Tools = @{}
    foreach ($Item in Get-ChildItem -Force -LiteralPath $Kit) {
        Safe $Kit $Item.Name | Out-Null
        if ($Item.Name -in @('KIT.json','KIT.sha256','baseline','runs')) { continue }
        if ($Item.PSIsContainer) {
            $Children = Inventory $Item.FullName
            foreach ($Name in $Children.Keys) { $Tools[$Item.Name+'/'+$Name] = $Children[$Name] }
        } else { $Tools[$Item.Name] = Hash $Item.FullName }
    }
    CheckInventory $Tools $Policy.toolFiles
    if ($VerifyOnly) { Write-Host 'Verified immutable kit; no run created and Java not executed'; exit 0 }
    $RunId = [guid]::NewGuid().ToString()
    if (-not $Output) {
        $Runs = Safe $Kit 'runs'
        [IO.Directory]::CreateDirectory($Runs) | Out-Null
        $Output = Join-Path $Runs ('run-'+$RunId)
    }
    $Output = [IO.Path]::GetFullPath($Output)
    Safe ([IO.Path]::GetDirectoryName($Output)) '' | Out-Null
    $Runs = Safe $Kit 'runs'
    if ($Output.StartsWith($Kit+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -and -not $Output.StartsWith($Runs+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Only runs/ may contain new runs within a kit' }
    if ((Test-Path -LiteralPath $Output) -or $Output -eq $Kit -or $Output -eq $Baseline -or $Output.StartsWith($Baseline+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $Output -match '(^|[\\/])(mods|saves)([\\/]|$)') { throw 'Choose a NEW isolated run directory; previous runs never reset' }
    # New-Item without -Force fails if another process claims this destination.
    New-Item -ItemType Directory -Path $Output -ErrorAction Stop | Out-Null
    foreach ($P in $Policy.baselineFiles.PSObject.Properties) {
        $Source = Safe $Baseline $P.Name
        if ((Hash $Source) -cne $P.Value) { throw 'Baseline changed during copy' }
        $Target = Safe $Output $P.Name
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Target)) | Out-Null
        [IO.File]::Copy($Source,$Target,$false)
        if ((Hash $Target) -cne $P.Value) { throw 'Copied run checksum mismatch' }
    }
    $PackagePath = Join-Path $Output 'PACKAGE.json'
    $Package = Get-Content -Raw -Encoding UTF8 -LiteralPath $PackagePath | ConvertFrom-Json
    $Old = $Package.authorization.worldId
    $Names = @(($Package.worldRoot+'/catancraft-fresh-demo.json'),'PROGRESSION_RUN.json','PROGRESSION_PLAN.json')
    $Names += @($Policy.baselineFiles.PSObject.Properties.Name | Where-Object { $_ -like 'evidence/results-*' })
    foreach ($Name in $Names) {
        $Path = Safe $Output $Name
        $Value = (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path).Replace($Old,$RunId) | ConvertFrom-Json
        WriteJson $Path $Value
    }
    $PlanHash = Hash (Safe $Output 'PROGRESSION_PLAN.json')
    foreach ($Name in $Names) {
        if ($Name -like 'evidence/results-*') {
            $Path = Safe $Output $Name
            $Value = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
            $Value.planSHA256 = $PlanHash
            WriteJson $Path $Value
        }
    }
    $Package = ($Package | ConvertTo-Json -Depth 100).Replace($Old,$RunId) | ConvertFrom-Json
    $Package.progressionRun.planSHA256 = $PlanHash
    foreach ($Name in $Names) {
        if ($Package.immutableFiles.PSObject.Properties.Name -contains $Name) { $Package.immutableFiles.$Name = Hash (Safe $Output $Name) }
        if ($Package.initialWorldFiles.PSObject.Properties.Name -contains $Name) { $Package.initialWorldFiles.$Name = Hash (Safe $Output $Name) }
    }
    $Package | Add-Member -NotePropertyName kitSHA256 -NotePropertyValue (Hash $Raw)
    WriteJson $PackagePath $Package
    [IO.File]::WriteAllText((Join-Path $Output 'PACKAGE.sha256'), ((Hash $PackagePath)+"  PACKAGE.json`n"))
    [IO.File]::Delete((Join-Path $Output '.catancraft-baseline-only'))
    Write-Host ('Created new isolated run: '+$Output)
} catch { [Console]::Error.WriteLine('REFUSED: '+$_.Exception.Message); exit 2 }
