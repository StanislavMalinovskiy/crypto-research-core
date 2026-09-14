[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'phase-manifest.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('phase-manifest-tests-' + [guid]::NewGuid().ToString('N'))
$failures = [System.Collections.Generic.List[string]]::new()

function Invoke-Manifest {
    param([Parameter(Mandatory)][string[]] $ArgumentList)

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.ArgumentList.Add('-NoProfile')
    $startInfo.ArgumentList.Add('-File')
    $startInfo.ArgumentList.Add($scriptPath)
    foreach ($argument in $ArgumentList) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = ($standardOutput + [Environment]::NewLine + $standardError).Trim()
    }
}

function Add-Failure {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)][string] $Message
    )

    $failures.Add("${Scenario}: $Message")
}

function Assert-ExitCode {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)] $Result,
        [Parameter(Mandatory)][int] $Expected
    )

    if ($Result.ExitCode -ne $Expected) {
        Add-Failure $Scenario "expected exit $Expected, got $($Result.ExitCode); output: $($Result.Output)"
    }
}

function Assert-OutputContains {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)] $Result,
        [Parameter(Mandatory)][string] $Expected
    )

    if (-not $Result.Output.Contains($Expected, [System.StringComparison]::Ordinal)) {
        Add-Failure $Scenario "expected output to contain '$Expected'; output: $($Result.Output)"
    }
}

function New-TestRepository {
    param([Parameter(Mandatory)][string] $Name)

    $repository = Join-Path $testRoot $Name
    [System.IO.Directory]::CreateDirectory((Join-Path $repository 'src/main')) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $repository 'tracked.txt'), "original`n")
    [System.IO.File]::WriteAllText((Join-Path $repository 'already-dirty.txt'), "committed`n")
    [System.IO.File]::WriteAllText((Join-Path $repository 'src/main/Allowed.txt'), "original`n")

    & git -C $repository init --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "git init failed for $repository"
    }
    & git -C $repository config user.email 'phase-manifest-tests@example.invalid'
    & git -C $repository config user.name 'Phase Manifest Tests'
    & git -C $repository add --all
    & git -C $repository commit --quiet -m baseline
    if ($LASTEXITCODE -ne 0) {
        throw "git commit failed for $repository"
    }

    return $repository
}

function New-Snapshot {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)][string] $Repository
    )

    $manifest = Join-Path $testRoot ($Scenario + '.json')
    $result = Invoke-Manifest @(
        '-Command', 'Snapshot',
        '-RepositoryRoot', $Repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $Scenario $result 0
    if (-not [System.IO.File]::Exists($manifest)) {
        Add-Failure $Scenario "snapshot did not create manifest outside repository: $manifest"
    }
    return $manifest
}

function Test-SnapshotOutsideRepository {
    $scenario = 'snapshot outside repository'
    $repository = New-TestRepository 'snapshot-output'
    $manifest = New-Snapshot $scenario $repository
    $repositoryPrefix = [System.IO.Path]::GetFullPath($repository).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if ([System.IO.Path]::GetFullPath($manifest).StartsWith(
            $repositoryPrefix,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-Failure $scenario "manifest was created inside repository: $manifest"
    }
}

function Test-UnchangedVerification {
    $scenario = 'unchanged verification'
    $repository = New-TestRepository 'unchanged'
    $manifest = New-Snapshot $scenario $repository
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 0
}

function Test-AllowlistedModification {
    $scenario = 'positive allowlist'
    $repository = New-TestRepository 'allowed'
    $manifest = New-Snapshot $scenario $repository
    [System.IO.File]::AppendAllText((Join-Path $repository 'src/main/Allowed.txt'), "allowed`n")
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest,
        '-AllowPath', 'src/main/**'
    )
    Assert-ExitCode $scenario $result 0
}

function Test-DisallowedModification {
    $scenario = 'disallowed modified file'
    $repository = New-TestRepository 'modified'
    $manifest = New-Snapshot $scenario $repository
    [System.IO.File]::AppendAllText((Join-Path $repository 'tracked.txt'), "changed`n")
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'MODIFIED tracked.txt'
}

function Test-DisallowedAddition {
    $scenario = 'disallowed added file'
    $repository = New-TestRepository 'added'
    $manifest = New-Snapshot $scenario $repository
    [System.IO.File]::WriteAllText((Join-Path $repository 'unexpected.txt'), "new`n")
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'ADDED unexpected.txt'
}

function Test-DisallowedDeletion {
    $scenario = 'disallowed deleted file'
    $repository = New-TestRepository 'deleted'
    $manifest = New-Snapshot $scenario $repository
    Remove-Item -LiteralPath (Join-Path $repository 'tracked.txt')
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'DELETED tracked.txt'
}

function Test-FileAlreadyDirtyAtSnapshot {
    $scenario = 'file already dirty at snapshot'
    $repository = New-TestRepository 'already-dirty'
    [System.IO.File]::AppendAllText((Join-Path $repository 'already-dirty.txt'), "before snapshot`n")
    $manifest = New-Snapshot $scenario $repository
    [System.IO.File]::AppendAllText((Join-Path $repository 'already-dirty.txt'), "after snapshot`n")
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'MODIFIED already-dirty.txt'
}

function Test-LowercaseRootGeneratedDirectoriesAreExcluded {
    $scenario = 'lowercase root generated directories are excluded'
    $repository = New-TestRepository 'root-target'
    $manifest = New-Snapshot $scenario $repository
    [System.IO.File]::WriteAllText((Join-Path $repository '.git/phase-test.tmp'), "ignored`n")
    [System.IO.Directory]::CreateDirectory((Join-Path $repository 'target')) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $repository 'target/generated.txt'), "generated`n")
    [System.IO.Directory]::CreateDirectory((Join-Path $repository '.codex-logs')) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $repository '.codex-logs/test.jsonl'), "ignored`n")
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 0
}

function Test-NestedTargetIsProtected {
    $scenario = 'nested target is protected'
    $repository = New-TestRepository 'nested-target'
    $manifest = New-Snapshot $scenario $repository
    [System.IO.Directory]::CreateDirectory((Join-Path $repository 'src/test/fixture/target')) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $repository 'src/test/fixture/target/evidence.txt'),
        "must be protected`n"
    )
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'ADDED src/test/fixture/target/evidence.txt'
}

function Test-DifferentlyCasedRootNamesAreProtectedOnCaseSensitiveFileSystems {
    $scenario = 'differently cased root names are protected on case-sensitive filesystems'
    $repository = New-TestRepository 'case-sensitive-root-names'
    $probe = Join-Path $repository 'case-probe'
    [System.IO.Directory]::CreateDirectory($probe) | Out-Null
    $caseSensitive = -not [System.IO.Directory]::Exists((Join-Path $repository 'CASE-PROBE'))
    Remove-Item -LiteralPath $probe
    if (-not $caseSensitive) {
        return
    }

    $manifest = New-Snapshot $scenario $repository
    foreach ($name in @('.GIT', 'Target', '.CODEX-LOGS')) {
        [System.IO.Directory]::CreateDirectory((Join-Path $repository $name)) | Out-Null
        [System.IO.File]::WriteAllText((Join-Path $repository "$name/evidence.txt"), "protected`n")
    }
    $result = Invoke-Manifest @(
        '-Command', 'Verify',
        '-RepositoryRoot', $repository,
        '-ManifestPath', $manifest
    )
    Assert-ExitCode $scenario $result 3
    Assert-OutputContains $scenario $result 'ADDED .GIT/evidence.txt'
    Assert-OutputContains $scenario $result 'ADDED Target/evidence.txt'
    Assert-OutputContains $scenario $result 'ADDED .CODEX-LOGS/evidence.txt'
}

function Test-RejectsManifestInsideRepository {
    $scenario = 'manifest inside repository'
    $repository = New-TestRepository 'inside-path'
    $result = Invoke-Manifest @(
        '-Command', 'Snapshot',
        '-RepositoryRoot', $repository,
        '-ManifestPath', (Join-Path $repository 'phase.json')
    )
    Assert-ExitCode $scenario $result 2
    Assert-OutputContains $scenario $result 'outside RepositoryRoot'
}

try {
    [System.IO.Directory]::CreateDirectory($testRoot) | Out-Null
    Test-SnapshotOutsideRepository
    Test-UnchangedVerification
    Test-AllowlistedModification
    Test-DisallowedModification
    Test-DisallowedAddition
    Test-DisallowedDeletion
    Test-FileAlreadyDirtyAtSnapshot
    Test-LowercaseRootGeneratedDirectoriesAreExcluded
    Test-NestedTargetIsProtected
    Test-DifferentlyCasedRootNamesAreProtectedOnCaseSensitiveFileSystems
    Test-RejectsManifestInsideRepository
}
finally {
    if ([System.IO.Directory]::Exists($testRoot)) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        [Console]::Error.WriteLine("FAIL: $failure")
    }
    exit 1
}

[Console]::Out.WriteLine('PASS: phase-manifest focused scenarios')
exit 0
