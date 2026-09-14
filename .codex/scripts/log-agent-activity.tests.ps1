[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$loggerPath = Join-Path $PSScriptRoot 'log-agent-activity.ps1'
$exporterPath = Join-Path $PSScriptRoot 'export-subagent-log.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('agent-activity-tests-' + [guid]::NewGuid().ToString('N'))
$failures = [System.Collections.Generic.List[string]]::new()

function Invoke-ScriptProcess {
    param([string] $ScriptPath, [string[]] $ArgumentList)

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.ArgumentList.Add('-NoProfile')
    $startInfo.ArgumentList.Add('-File')
    $startInfo.ArgumentList.Add($ScriptPath)
    foreach ($argument in $ArgumentList) {
        $startInfo.ArgumentList.Add($argument)
    }
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $output = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.Trim() }
}

function Add-Failure([string] $Scenario, [string] $Message) {
    $failures.Add("${Scenario}: $Message")
}

function Assert-Exit([string] $Scenario, [object] $Result, [int] $Expected) {
    if ($Result.ExitCode -ne $Expected) {
        Add-Failure $Scenario "expected exit $Expected, got $($Result.ExitCode): $($Result.Output)"
    }
}

function Test-CompletedAssignmentWithTokens {
    $scenario = 'completed assignment with tokens'
    $logDirectory = Join-Path $testRoot 'complete'
    $dispatch = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Dispatch', '-AssignmentId', 'assignment-1', '-Role', 'tester', '-Phase', 'tests-red',
        '-Summary', "Write requirement-derived`nred test", '-AgentId', 'agent-7', '-LogDirectory', $logDirectory,
        '-Timestamp', '2026-09-15T23:23:00+05:00'
    )
    $return = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Return', '-AssignmentId', 'assignment-1', '-Summary', 'RED_CANDIDATE for scenario A',
        '-Status', 'RED_CANDIDATE', '-InputTokens', '1200', '-CachedInputTokens', '300',
        '-OutputTokens', '240', '-ReasoningTokens', '80', '-TotalTokens', '1440',
        '-LogDirectory', $logDirectory, '-Timestamp', '2026-09-15T23:24:05+05:00'
    )
    Assert-Exit "$scenario dispatch" $dispatch 0
    Assert-Exit "$scenario return" $return 0

    $records = @(Get-Content (Join-Path $logDirectory 'subagents.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    if ($records.Count -ne 2 -or $records[1].duration_ms -ne 65000 -or $records[1].phase -cne 'tests-red') {
        Add-Failure $scenario 'machine records do not preserve correlation, phase, or duration'
    }
    if ($records[1].token_usage.input -ne 1200 -or $records[1].token_usage.reasoning -ne 80) {
        Add-Failure $scenario 'machine return does not preserve supplied token usage'
    }
    $readable = Get-Content -Raw (Join-Path $logDirectory 'subagents-readable.log')
    if ($readable -notmatch '^15-09-26 23:23 \|' -or $readable -notmatch 'duration=00:01:05' -or
            $readable -notmatch 'Architect -> Tester: Write requirement-derived red test' -or
            $readable -notmatch 'input=1200.*reasoning=80.*total=1440') {
        Add-Failure $scenario "unexpected readable line: $readable"
    }
}

function Test-UnavailableTokensAndValidation {
    $scenario = 'unavailable tokens and validation'
    $logDirectory = Join-Path $testRoot 'unavailable'
    $dispatch = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Dispatch', '-AssignmentId', 'assignment-2', '-Role', 'reviewer', '-Phase', 'threat-check',
        '-Summary', 'Check bypass classes', '-LogDirectory', $logDirectory,
        '-Timestamp', '2026-09-15T20:00:00Z'
    )
    $return = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Return', '-AssignmentId', 'assignment-2', '-Summary', 'No blocking bypass found',
        '-Status', 'THREAT_CHECK_PASSED', '-LogDirectory', $logDirectory,
        '-Timestamp', '2026-09-15T20:02:00Z'
    )
    $duplicate = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Return', '-AssignmentId', 'assignment-2', '-Summary', 'Duplicate',
        '-Status', 'THREAT_CHECK_PASSED', '-LogDirectory', $logDirectory,
        '-Timestamp', '2026-09-15T20:03:00Z'
    )
    $invalidDispatch = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Dispatch', '-AssignmentId', 'invalid-phase', '-Role', 'tester', '-Phase', 'implementation',
        '-Summary', 'Invalid role phase', '-LogDirectory', $logDirectory
    )
    $invalidStatusDispatch = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Dispatch', '-AssignmentId', 'invalid-status', '-Role', 'tester', '-Phase', 'tests-red',
        '-Summary', 'Prepare test', '-LogDirectory', $logDirectory
    )
    $invalidStatusReturn = Invoke-ScriptProcess $loggerPath @(
        '-Action', 'Return', '-AssignmentId', 'invalid-status', '-Summary', 'Wrong role status',
        '-Status', 'APPROVE', '-LogDirectory', $logDirectory
    )
    Assert-Exit "$scenario dispatch" $dispatch 0
    Assert-Exit "$scenario return" $return 0
    Assert-Exit "$scenario duplicate" $duplicate 2
    Assert-Exit "$scenario invalid phase" $invalidDispatch 2
    Assert-Exit "$scenario invalid status dispatch" $invalidStatusDispatch 0
    Assert-Exit "$scenario invalid status return" $invalidStatusReturn 2
    $readable = Get-Content -Raw (Join-Path $logDirectory 'subagents-readable.log')
    if ($readable -notmatch 'phase=threat-check' -or $readable -notmatch 'input=unavailable' -or
            $readable -notmatch 'duration=00:02:00') {
        Add-Failure $scenario "missing explicit unavailable telemetry: $readable"
    }
}

function Test-LegacyExport {
    $scenario = 'legacy export'
    $source = Join-Path $testRoot 'legacy.jsonl'
    $destination = Join-Path $testRoot 'legacy-readable.log'
    $legacy = @(
        '{"timestamp_utc":"2026-09-15T18:23:00Z","event":"SubagentStart","agent_id":"a1","agent_type":"tester"}',
        '{"timestamp_utc":"2026-09-15T18:25:30Z","event":"SubagentStop","agent_id":"a1","agent_type":"tester"}',
        '{"timestamp_utc":"2026-09-15T19:00:00Z","event":"SubagentStop","agent_id":"a1","agent_type":"tester"}'
    )
    [System.IO.File]::WriteAllLines($source, $legacy, [System.Text.UTF8Encoding]::new($false))
    $result = Invoke-ScriptProcess $exporterPath @('-SourcePath', $source, '-DestinationPath', $destination)
    Assert-Exit $scenario $result 0
    $lines = @(Get-Content $destination)
    if ($lines.Count -ne 2 -or $lines[0] -notmatch 'duration=00:02:30' -or
            $lines[1] -notmatch 'duration=unavailable' -or $lines[0] -notmatch 'legacy lifecycle only') {
        Add-Failure $scenario "legacy correlation was not exported honestly: $($lines -join ' / ')"
    }
}

try {
    [System.IO.Directory]::CreateDirectory($testRoot) | Out-Null
    Test-CompletedAssignmentWithTokens
    Test-UnavailableTokensAndValidation
    Test-LegacyExport
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

[Console]::Out.WriteLine('PASS: agent activity logging focused scenarios')
exit 0
