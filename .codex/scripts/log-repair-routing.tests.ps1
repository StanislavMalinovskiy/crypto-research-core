[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'log-repair-routing.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('repair-routing-tests-' + [guid]::NewGuid().ToString('N'))
$failures = [System.Collections.Generic.List[string]]::new()

function New-LoggerStartInfo {
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
    return $startInfo
}

function Complete-Process {
    param([Parameter(Mandatory)] $Process)

    $standardOutput = $Process.StandardOutput.ReadToEnd()
    $standardError = $Process.StandardError.ReadToEnd()
    $Process.WaitForExit()
    return [pscustomobject]@{
        ExitCode = $Process.ExitCode
        Output = ($standardOutput + [Environment]::NewLine + $standardError).Trim()
    }
}

function Invoke-Logger {
    param([Parameter(Mandatory)][string[]] $ArgumentList)

    return Complete-Process ([System.Diagnostics.Process]::Start((New-LoggerStartInfo $ArgumentList)))
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

function Test-ValidRoutingRecord {
    $scenario = 'valid routing record'
    $logPath = Join-Path $testRoot 'valid.jsonl'
    $result = Invoke-Logger @(
        '-Loop', 'AUDIT',
        '-SourceStatus', 'AUDIT_FAILED',
        '-RepairOwner', 'Tester',
        '-Round', '1',
        '-LogPath', $logPath
    )
    Assert-ExitCode $scenario $result 0
    if (-not [System.IO.File]::Exists($logPath)) {
        Add-Failure $scenario "expected JSONL file: $logPath"
        return
    }

    $lines = @(Get-Content -LiteralPath $logPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -ne 1) {
        Add-Failure $scenario "expected one JSONL record, got $($lines.Count)"
        return
    }

    try {
        $record = $lines[0] | ConvertFrom-Json
    }
    catch {
        Add-Failure $scenario "record is not valid JSON: $($_.Exception.Message)"
        return
    }

    $expectedFields = @('loop', 'repair_owner', 'round', 'source_status', 'timestamp_utc')
    $actualFields = @($record.PSObject.Properties.Name | Sort-Object)
    if (Compare-Object $expectedFields $actualFields) {
        Add-Failure $scenario "expected fields $($expectedFields -join ', '); got $($actualFields -join ', ')"
    }
    if ($record.loop -cne 'AUDIT' -or $record.source_status -cne 'AUDIT_FAILED' -or
            $record.repair_owner -cne 'Tester' -or [int] $record.round -ne 1) {
        Add-Failure $scenario 'record does not preserve the supplied routing fields'
    }

    $timestamp = [System.DateTimeOffset]::MinValue
    $timestampValid = [System.DateTimeOffset]::TryParse(
        [string] $record.timestamp_utc,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::RoundtripKind,
        [ref] $timestamp
    )
    if (-not $timestampValid -or $timestamp.Offset -ne [System.TimeSpan]::Zero) {
        Add-Failure $scenario "timestamp_utc is not a parseable UTC timestamp: $($record.timestamp_utc)"
    }
}

function Test-InvalidInput {
    $scenario = 'invalid input'
    $logPath = Join-Path $testRoot 'invalid.jsonl'
    $invalidRound = Invoke-Logger @(
        '-Loop', 'AUDIT',
        '-SourceStatus', 'AUDIT_FAILED',
        '-RepairOwner', 'Tester',
        '-Round', '0',
        '-LogPath', $logPath
    )
    $blankLoop = Invoke-Logger @(
        '-Loop', '   ',
        '-SourceStatus', 'AUDIT_FAILED',
        '-RepairOwner', 'Tester',
        '-Round', '1',
        '-LogPath', $logPath
    )
    Assert-ExitCode "$scenario round" $invalidRound 1
    Assert-ExitCode "$scenario blank loop" $blankLoop 1
    if ([System.IO.File]::Exists($logPath)) {
        Add-Failure $scenario 'invalid invocation created a log record'
    }
}

function Test-ConcurrentAppends {
    $scenario = 'concurrent appends'
    $logPath = Join-Path $testRoot 'concurrent.jsonl'
    $processes = [System.Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt 16; $index++) {
        $round = ($index % 3) + 1
        $arguments = @(
            '-Loop', "AUDIT-$index",
            '-SourceStatus', 'AUDIT_FAILED',
            '-RepairOwner', "Tester-$index",
            '-Round', [string] $round,
            '-LogPath', $logPath
        )
        $processes.Add([System.Diagnostics.Process]::Start((New-LoggerStartInfo $arguments)))
    }

    $results = @($processes | ForEach-Object { Complete-Process $_ })
    $failedResults = @($results | Where-Object { $_.ExitCode -ne 0 })
    if ($failedResults.Count -gt 0) {
        $exitCodes = ($failedResults.ExitCode | Sort-Object -Unique) -join ', '
        Add-Failure $scenario "$($failedResults.Count) of 16 writers failed; exit codes: $exitCodes"
    }
    if (-not [System.IO.File]::Exists($logPath)) {
        Add-Failure $scenario "expected JSONL file: $logPath"
        return
    }

    $lines = @(Get-Content -LiteralPath $logPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines.Count -ne 16) {
        Add-Failure $scenario "expected 16 complete records, got $($lines.Count)"
    }

    $records = [System.Collections.Generic.List[object]]::new()
    foreach ($line in $lines) {
        try {
            $records.Add(($line | ConvertFrom-Json))
        }
        catch {
            Add-Failure $scenario "corrupt JSONL record: $line"
        }
    }
    $uniqueLoops = @($records.loop | Sort-Object -Unique)
    if ($uniqueLoops.Count -ne 16) {
        Add-Failure $scenario "expected 16 unique routing records, got $($uniqueLoops.Count)"
    }
}

try {
    [System.IO.Directory]::CreateDirectory($testRoot) | Out-Null
    Test-ValidRoutingRecord
    Test-InvalidInput
    Test-ConcurrentAppends
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

[Console]::Out.WriteLine('PASS: repair-routing logger focused scenarios')
exit 0
