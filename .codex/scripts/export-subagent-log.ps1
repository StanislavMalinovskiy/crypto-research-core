[CmdletBinding()]
param(
    [string] $SourcePath = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.codex-logs/subagents.jsonl'),
    [string] $DestinationPath = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.codex-logs/subagents-readable.log')
)

$ErrorActionPreference = 'Stop'

function Get-RecordValue {
    param([object] $Record, [string] $Name)

    $property = $Record.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Format-Role {
    param([object] $Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string] $Value)) {
        return 'Unknown'
    }
    return (Get-Culture).TextInfo.ToTitleCase(([string] $Value).ToLowerInvariant())
}

function Format-Time {
    param([object] $Value)

    return ([DateTimeOffset] $Value).ToLocalTime().ToString(
        'dd-MM-yy HH:mm',
        [System.Globalization.CultureInfo]::InvariantCulture
    )
}

function Format-Duration {
    param([object] $Milliseconds)

    if ($null -eq $Milliseconds) {
        return 'unavailable'
    }
    $duration = [TimeSpan]::FromMilliseconds([long] $Milliseconds)
    $hours = [Math]::Floor($duration.TotalHours)
    return '{0:00}:{1:00}:{2:00}' -f $hours, $duration.Minutes, $duration.Seconds
}

function Format-Token {
    param([object] $Value)

    if ($null -eq $Value) {
        return 'unavailable'
    }
    return [string] $Value
}

function Format-AssignmentReturn {
    param([object] $Record)

    $role = Format-Role (Get-RecordValue $Record 'role')
    $usage = Get-RecordValue $Record 'token_usage'
    $inputTokens = if ($null -eq $usage) { $null } else { Get-RecordValue $usage 'input' }
    $cachedTokens = if ($null -eq $usage) { $null } else { Get-RecordValue $usage 'cached_input' }
    $outputTokens = if ($null -eq $usage) { $null } else { Get-RecordValue $usage 'output' }
    $reasoningTokens = if ($null -eq $usage) { $null } else { Get-RecordValue $usage 'reasoning' }
    $totalTokens = if ($null -eq $usage) { $null } else { Get-RecordValue $usage 'total' }
    $tokens = 'input={0}, cached_input={1}, output={2}, reasoning={3}, total={4}' -f
        (Format-Token $inputTokens),
        (Format-Token $cachedTokens),
        (Format-Token $outputTokens),
        (Format-Token $reasoningTokens),
        (Format-Token $totalTokens)
    return '{0} | Architect -> {1}: {2} | {1} -> Architect: STATUS: {3}; {4} | phase={5} | duration={6} | tokens: {7}' -f
        (Format-Time (Get-RecordValue $Record 'started_at_utc')),
        $role,
        (Get-RecordValue $Record 'task_summary'),
        (Get-RecordValue $Record 'status'),
        (Get-RecordValue $Record 'result_summary'),
        (Get-RecordValue $Record 'phase'),
        (Format-Duration (Get-RecordValue $Record 'duration_ms')),
        $tokens
}

try {
    $sourceFullPath = [System.IO.Path]::GetFullPath($SourcePath)
    $destinationFullPath = [System.IO.Path]::GetFullPath($DestinationPath)
    if (-not [System.IO.File]::Exists($sourceFullPath)) {
        throw "Source JSONL does not exist: $sourceFullPath"
    }

    $starts = @{}
    $readableLines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in [System.IO.File]::ReadLines($sourceFullPath)) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $record = $line | ConvertFrom-Json
        $eventName = [string] (Get-RecordValue $record 'event')
        if ($eventName -eq 'AssignmentReturn') {
            $readableLines.Add((Format-AssignmentReturn $record))
            continue
        }
        if ($eventName -eq 'SubagentStart') {
            $agentId = [string] (Get-RecordValue $record 'agent_id')
            if (-not [string]::IsNullOrWhiteSpace($agentId)) {
                $starts[$agentId] = $record
            }
            continue
        }
        if ($eventName -ne 'SubagentStop') {
            continue
        }

        $stopAgentId = [string] (Get-RecordValue $record 'agent_id')
        $role = Format-Role (Get-RecordValue $record 'agent_type')
        $stopTimestamp = Get-RecordValue $record 'timestamp_utc'
        $displayTimestamp = Format-Time $stopTimestamp
        $duration = 'unavailable'
        if ($starts.ContainsKey($stopAgentId)) {
            $startRecord = $starts[$stopAgentId]
            $startedAt = [DateTimeOffset] (Get-RecordValue $startRecord 'timestamp_utc')
            $endedAt = [DateTimeOffset] $stopTimestamp
            $duration = Format-Duration ([long] [Math]::Round(($endedAt - $startedAt).TotalMilliseconds))
            $displayTimestamp = Format-Time (Get-RecordValue $startRecord 'timestamp_utc')
            $starts.Remove($stopAgentId)
        }
        $readableLines.Add(
            "$displayTimestamp | Architect -> ${role}: unavailable (legacy lifecycle only) | ${role} -> Architect: unavailable | phase=unavailable | duration=$duration | tokens: input=unavailable, cached_input=unavailable, output=unavailable, reasoning=unavailable, total=unavailable"
        )
    }

    $destinationDirectory = [System.IO.Path]::GetDirectoryName($destinationFullPath)
    [System.IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
    [System.IO.File]::WriteAllLines(
        $destinationFullPath,
        $readableLines,
        [System.Text.UTF8Encoding]::new($false)
    )
    [Console]::Out.WriteLine("Wrote $($readableLines.Count) readable assignment lines to $destinationFullPath")
    exit 0
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
