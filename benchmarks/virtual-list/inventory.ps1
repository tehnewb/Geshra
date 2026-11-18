<#
Records actual packaged fixture byte identity without extracting or trusting source timestamps.
Library/runtime differences are expected; application classes and static fixture resources must match.
#>
param([string[]]$Versions = @('before', 'after'), [string]$Output = 'inventory.json')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$inventory = [ordered]@{}
foreach ($version in $Versions) {
    $artifact = [IO.Path]::GetFullPath("$PSScriptRoot/../../build/virtual-list-$version.jar")
    $archive = [IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        $entries = [ordered]@{}
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '^BOOT-INF/classes/(virtualbench/.+\.class|web/.+)$') { continue }
            $stream = $entry.Open()
            try {
                $entries[$entry.FullName] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()
            } finally {
                $stream.Dispose()
            }
        }
        $inventory[$version] = [ordered]@{ artifact = $artifact; sha256 = (Get-FileHash -LiteralPath $artifact).Hash.ToLowerInvariant(); entries = $entries }
    } finally {
        $archive.Dispose()
    }
}
foreach ($version in $Versions) {
    foreach ($name in $inventory.before.entries.Keys) {
        if ($inventory.before.entries[$name] -ne $inventory[$version].entries[$name]) { throw "Changed fixture entry: $name" }
    }
    if ($inventory.before.entries.Count -ne $inventory[$version].entries.Count) { throw 'Fixture entry count mismatch' }
}
$outputPath = [IO.Path]::GetFullPath("$PSScriptRoot/../../performance-results/virtual-list/$Output")
[IO.File]::WriteAllText($outputPath, ($inventory | ConvertTo-Json -Depth 6) + "`n")
Write-Output "Verified $($inventory.before.entries.Count) identical fixture entries"
