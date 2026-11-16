param([switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function Safe([string]$Name) {
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
function Check([string]$Name,[string]$Pin) { if ((Hash (Safe $Name)) -cne $Pin) { throw ('Changed/missing required file: '+$Name) } }
function WriteJson([string]$Path,$Value) { [IO.File]::WriteAllText($Path,(($Value | ConvertTo-Json -Depth 100)+"`n"),(New-Object Text.UTF8Encoding($false))) }
function Files([string]$Name) {
    $Found = @()
    $Folder = Safe $Name
    if (-not (Test-Path -LiteralPath $Folder)) { return $Found }
    $Pending = New-Object 'Collections.Generic.Stack[string]'
    $Pending.Push($Folder)
    while ($Pending.Count -gt 0) {
        foreach ($Item in Get-ChildItem -Force -LiteralPath $Pending.Pop()) {
            $Relative = $Item.FullName.Substring($Root.Length+1).Replace('\','/')
            Safe $Relative | Out-Null
            if ($Item.PSIsContainer) { $Pending.Push($Item.FullName) } else { $Found += $Relative }
        }
    }
    return $Found
}
function SameSet($Actual,$Expected) {
    if (@($Actual).Count -ne @($Expected).Count) { throw 'Changed/extra file inventory' }
    foreach ($Name in $Actual) { if ($Expected -cnotcontains $Name) { throw 'Changed/extra file inventory' } }
}
try {
    if (Test-Path -LiteralPath (Safe '.catancraft-baseline-only')) { throw 'Immutable baseline cannot launch; use NEW_TEST_RUN' }
    $PackagePath = Safe 'PACKAGE.json'
    if ((Hash $PackagePath) -cne ((Get-Content -Raw -LiteralPath (Safe 'PACKAGE.sha256')).Split()[0])) { throw 'Package manifest checksum mismatch' }
    $Policy = Get-Content -Raw -LiteralPath $PackagePath | ConvertFrom-Json
    foreach ($P in $Policy.immutableFiles.PSObject.Properties) { Check $P.Name $P.Value }
    $Scripts = @(Get-ChildItem -Force -LiteralPath $Root | Where-Object { $_.Extension -in @('.ps1','.py','.bat','.sh','.cmd') } | ForEach-Object { $_.Name })
    $ExpectedScripts = @($Policy.immutableFiles.PSObject.Properties.Name | Where-Object { $_ -notmatch '/' -and [IO.Path]::GetExtension($_) -in @('.ps1','.py','.bat','.sh','.cmd') })
    SameSet $Scripts $ExpectedScripts
    foreach ($Prefix in @('mods','libraries','runtime','tools')) {
        $Expected = @($Policy.immutableFiles.PSObject.Properties.Name | Where-Object { $_.StartsWith($Prefix+'/') })
        SameSet @(Files $Prefix) $Expected
    }
    $Properties = @{}
    $Text = Get-Content -Raw -LiteralPath (Safe 'server.properties')
    if ($Text -match '[\x00-\x09\x0B\x0C\x0E-\x1F\x7F\x85\u2028\u2029]') { throw 'Unsupported property control/separator' }
    foreach ($Line in ($Text -split '\r\n|\r|\n')) {
        if (-not $Line.Trim() -or $Line.TrimStart().StartsWith('#') -or $Line.TrimStart().StartsWith('!')) { continue }
        if ($Line -notmatch '^([A-Za-z0-9_.-]+)=(.*)$' -or $Properties.ContainsKey($Matches[1])) { throw 'Unsupported/duplicate server property' }
        $Key = $Matches[1]; $Value = $Matches[2]
        if (($Value.Length-$Value.TrimEnd('\').Length) % 2) { throw 'Continued property refused' }
        $Value = [regex]::Replace($Value,'\\(u[0-9a-fA-F]{4}|.)',{ param($M)
            $T=$M.Groups[1].Value
            if ($T.StartsWith('u')) { return [char][Convert]::ToInt32($T.Substring(1),16) }
            switch ($T) { 'n' { return "`n" } 'r' { return "`r" } 't' { return "`t" } 'f' { return "`f" } default { return $T } }
        })
        $Properties[$Key]=$Value
    }
    foreach ($P in $Policy.serverProperties.PSObject.Properties) { if (-not $Properties.ContainsKey($P.Name) -or $Properties[$P.Name] -cne $P.Value) { throw ('Changed required server property: '+$P.Name) } }
    $Marker = Safe '.demo-launch-state.json'
    $AuthPath = Safe ($Policy.worldRoot+'/catancraft-fresh-demo.json')
    $Auth = Get-Content -Raw -LiteralPath $AuthPath | ConvertFrom-Json
    $WorldFiles = @(Files $Policy.worldRoot)
    if (Test-Path -LiteralPath $Marker) {
        $Binding = Get-Content -Raw -LiteralPath $Marker | ConvertFrom-Json
        if ($Binding.root -cne $Root -or $Binding.inputManifestSHA256 -cne $Policy.inputManifestSHA256 -or @($Binding.PSObject.Properties).Count -ne 2) { throw 'Run moved or binding changed; preserve it' }
        if ($Auth.installationBoundPath -cne (Safe $Policy.worldRoot) -or @($Auth.PSObject.Properties).Count -ne (@($Policy.authorization.PSObject.Properties).Count+1)) { throw 'World authorization binding changed' }
        foreach ($P in $Policy.authorization.PSObject.Properties) { if ($Auth.($P.Name) -cne $P.Value) { throw 'World authorization changed' } }
    } else {
        Check 'server.properties' $Policy.initialServerPropertiesSHA256
        SameSet $WorldFiles @($Policy.initialWorldFiles.PSObject.Properties.Name)
        foreach ($P in $Policy.initialWorldFiles.PSObject.Properties) { Check $P.Name $P.Value }
    }
    if ($VerifyOnly) { Write-Host 'Verified run files/binding; Java not executed, server not started'; exit 0 }
    if ((Get-Content -Raw -LiteralPath (Safe 'eula.txt')) -notmatch '(?m)^\s*eula\s*=\s*true\s*$') { throw 'Read Minecraft EULA and personally set eula=true in this run before launch' }
    $Java = Safe 'runtime/java17/bin/java.exe'
    if (-not (Test-Path -LiteralPath $Java)) { throw 'Bundled Java 17 missing; use the complete Windows kit' }
    $ErrorActionPreference = 'Continue'
    $Details = (& $Java '-XshowSettings:properties' '-version' 2>&1 | Out-String)
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0 -or $Details -notmatch 'java\.version\s*=\s*17(?:\.|\s)' -or $Details -notmatch 'sun\.arch\.data\.model\s*=\s*64\b') { throw 'Exactly 64-bit Java 17 required' }
    $ArgFile = 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
    if ($Policy.forgeRuntimeFiles.PSObject.Properties.Name -notcontains $ArgFile) { throw 'Pinned official Forge argument file missing' }
    if (-not (Test-Path -LiteralPath $Marker)) {
        $Auth | Add-Member -NotePropertyName installationBoundPath -NotePropertyValue (Safe $Policy.worldRoot)
        WriteJson $AuthPath $Auth
        $Binding = @{root=$Root; inputManifestSHA256=$Policy.inputManifestSHA256}
        $Stream = [IO.File]::Open($Marker,[IO.FileMode]::CreateNew)
        $Stream.Dispose()
        WriteJson $Marker $Binding
    }
    Set-Location -LiteralPath $Root
    & $Java '-Xms1G' '-Xmx4G' ('@'+$ArgFile) 'nogui'
    exit $LASTEXITCODE
} catch { [Console]::Error.WriteLine('REFUSED: '+$_.Exception.Message); exit 2 }
