[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Get-HookValue {
    param(
        [Parameter(Mandatory)] [object] $InputObject,
        [Parameter(Mandatory)] [string] $Name
    )

    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

$rawInput = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($rawInput)) {
    exit 0
}

$event = $rawInput | ConvertFrom-Json
$eventName = Get-HookValue -InputObject $event -Name 'hook_event_name'
if ($eventName -notin @('SessionStart', 'SubagentStart', 'SubagentStop')) {
    exit 0
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$logDirectory = Join-Path $repositoryRoot '.codex-logs'
$logName = if ($eventName -eq 'SessionStart') { 'hooks.jsonl' } else { 'subagents.jsonl' }
$logPath = Join-Path $logDirectory $logName
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$record = [ordered]@{
    timestamp_utc  = [DateTimeOffset]::UtcNow.ToString('O')
    event          = $eventName
    session_id     = Get-HookValue -InputObject $event -Name 'session_id'
    turn_id        = Get-HookValue -InputObject $event -Name 'turn_id'
    agent_id       = Get-HookValue -InputObject $event -Name 'agent_id'
    agent_type     = Get-HookValue -InputObject $event -Name 'agent_type'
    model          = Get-HookValue -InputObject $event -Name 'model'
    permission_mode = Get-HookValue -InputObject $event -Name 'permission_mode'
}

if ($eventName -eq 'SubagentStop') {
    $record.transcript_available = $null -ne (Get-HookValue -InputObject $event -Name 'agent_transcript_path')
}

$line = ($record | ConvertTo-Json -Compress) + [Environment]::NewLine
$bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($line)

for ($attempt = 1; $attempt -le 5; $attempt++) {
    try {
        $stream = [System.IO.FileStream]::new(
            $logPath,
            [System.IO.FileMode]::Append,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::Read
        )
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
        if ($eventName -eq 'SubagentStop') {
            [Console]::Out.Write('{}')
        }
        exit 0
    }
    catch [System.IO.IOException] {
        if ($attempt -eq 5) {
            throw
        }
        Start-Sleep -Milliseconds (20 * $attempt)
    }
}
