[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Dispatch', 'Return')]
    [string] $Action,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._-]{1,128}$')]
    [string] $AssignmentId,

    [ValidateSet('developer', 'tester', 'reviewer', 'researcher')]
    [string] $Role,

    [ValidateSet('research', 'threat-check', 'skeleton', 'tests-red', 'tests-evidence', 'implementation', 'adjudicate', 'audit')]
    [string] $Phase,

    [Parameter(Mandatory)]
    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $Summary,

    [string] $Status,

    [long] $InputTokens = -1,
    [long] $CachedInputTokens = -1,
    [long] $OutputTokens = -1,
    [long] $ReasoningTokens = -1,
    [long] $TotalTokens = -1,

    [string] $AgentId,

    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $LogDirectory = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.codex-logs'),

    [DateTimeOffset] $Timestamp = [DateTimeOffset]::UtcNow
)

$ErrorActionPreference = 'Stop'

$allowedStatuses = @(
    'SKELETON_READY', 'IMPL_DONE', 'TEST_SUSPECT', 'BLOCKED',
    'RED_CANDIDATE', 'EVIDENCE_CANDIDATE', 'SPEC_INCOMPLETE',
    'RESEARCH_DONE', 'INCONCLUSIVE',
    'THREAT_CHECK_PASSED', 'THREATS_FOUND',
    'CODE_WRONG', 'TEST_WRONG', 'SPEC_AMBIGUOUS',
    'AUDIT_FAILED', 'APPROVE'
)
$rolePhases = @{
    developer = @('skeleton', 'implementation')
    tester = @('tests-red', 'tests-evidence')
    reviewer = @('threat-check', 'adjudicate', 'audit')
    researcher = @('research')
}
$roleStatuses = @{
    developer = @('SKELETON_READY', 'IMPL_DONE', 'TEST_SUSPECT', 'BLOCKED')
    tester = @('RED_CANDIDATE', 'EVIDENCE_CANDIDATE', 'SPEC_INCOMPLETE', 'TEST_SUSPECT', 'BLOCKED')
    researcher = @('RESEARCH_DONE', 'INCONCLUSIVE', 'BLOCKED')
}
$reviewerStatuses = @{
    'threat-check' = @('THREAT_CHECK_PASSED', 'THREATS_FOUND')
    adjudicate = @('CODE_WRONG', 'TEST_WRONG', 'SPEC_AMBIGUOUS')
    audit = @('AUDIT_FAILED', 'APPROVE')
}

function ConvertTo-OneLine {
    param([Parameter(Mandatory)][string] $Value)

    $oneLine = [regex]::Replace($Value, '\s+', ' ').Trim()
    if ($oneLine.Length -gt 400) {
        throw 'Summary must contain at most 400 characters after whitespace normalization.'
    }
    return $oneLine
}

function Write-AppendLine {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $Line
    )

    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Line + [Environment]::NewLine)
    for ($attempt = 1; $attempt -le 100; $attempt++) {
        $stream = $null
        try {
            $stream = [System.IO.FileStream]::new(
                $Path,
                [System.IO.FileMode]::OpenOrCreate,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::None
            )
            [void] $stream.Seek(0, [System.IO.SeekOrigin]::End)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
            return
        }
        catch [System.IO.IOException] {
            if ($attempt -eq 100) {
                throw
            }
            Start-Sleep -Milliseconds 20
        }
        finally {
            if ($null -ne $stream) {
                $stream.Dispose()
            }
        }
    }
}

function Format-TokenValue {
    param([long] $Value)

    if ($Value -lt 0) {
        return 'unavailable'
    }
    return [string] $Value
}

function Format-Duration {
    param([long] $Milliseconds)

    $duration = [TimeSpan]::FromMilliseconds($Milliseconds)
    $hours = [Math]::Floor($duration.TotalHours)
    return '{0:00}:{1:00}:{2:00}' -f $hours, $duration.Minutes, $duration.Seconds
}

try {
    foreach ($tokenValue in @($InputTokens, $CachedInputTokens, $OutputTokens, $ReasoningTokens, $TotalTokens)) {
        if ($tokenValue -lt -1) {
            throw 'Token values must be non-negative or omitted.'
        }
    }

    $normalizedSummary = ConvertTo-OneLine $Summary
    $logFullDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
    [System.IO.Directory]::CreateDirectory($logFullDirectory) | Out-Null
    $stateDirectory = Join-Path $logFullDirectory 'subagent-assignments'
    [System.IO.Directory]::CreateDirectory($stateDirectory) | Out-Null
    $statePath = Join-Path $stateDirectory ($AssignmentId + '.json')
    $machineLogPath = Join-Path $logFullDirectory 'subagents.jsonl'
    $readableLogPath = Join-Path $logFullDirectory 'subagents-readable.log'
    $timestampUtc = $Timestamp.ToUniversalTime()

    if ($Action -eq 'Dispatch') {
        if ([string]::IsNullOrWhiteSpace($Role) -or [string]::IsNullOrWhiteSpace($Phase)) {
            throw 'Dispatch requires Role and Phase.'
        }
        if ($Phase -cnotin $rolePhases[$Role]) {
            throw "Phase '$Phase' is not valid for role '$Role'."
        }
        if (-not [string]::IsNullOrWhiteSpace($Status)) {
            throw 'Dispatch does not accept Status.'
        }

        $state = [ordered]@{
            assignment_id = $AssignmentId
            agent_id = if ([string]::IsNullOrWhiteSpace($AgentId)) { $null } else { $AgentId }
            role = $Role
            phase = $Phase
            task_summary = $normalizedSummary
            started_at_utc = $timestampUtc.ToString('O')
            completed = $false
        }
        $stateBytes = [System.Text.UTF8Encoding]::new($false).GetBytes(($state | ConvertTo-Json -Compress))
        $stateStream = [System.IO.FileStream]::new(
            $statePath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None
        )
        try {
            $stateStream.Write($stateBytes, 0, $stateBytes.Length)
            $stateStream.Flush($true)
        }
        finally {
            $stateStream.Dispose()
        }

        $record = [ordered]@{
            timestamp_utc = $timestampUtc.ToString('O')
            event = 'AssignmentDispatch'
            assignment_id = $AssignmentId
            agent_id = $state.agent_id
            role = $Role
            phase = $Phase
            task_summary = $normalizedSummary
        }
        Write-AppendLine $machineLogPath ($record | ConvertTo-Json -Compress -Depth 4)
        exit 0
    }

    if (-not [System.IO.File]::Exists($statePath)) {
        throw "No dispatch state exists for assignment '$AssignmentId'."
    }
    if ([string]::IsNullOrWhiteSpace($Status) -or $Status -cnotin $allowedStatuses) {
        throw 'Return requires one status from the canonical role protocol.'
    }
    if (-not [string]::IsNullOrWhiteSpace($Role) -or -not [string]::IsNullOrWhiteSpace($Phase)) {
        throw 'Return derives Role and Phase from the recorded dispatch.'
    }

    $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    if ($state.completed) {
        throw "Assignment '$AssignmentId' has already been completed."
    }
    $expectedStatuses = if ($state.role -ceq 'reviewer') {
        $reviewerStatuses[[string] $state.phase]
    }
    else {
        $roleStatuses[[string] $state.role]
    }
    if ($Status -cnotin $expectedStatuses) {
        throw "Status '$Status' is not valid for role '$($state.role)' in phase '$($state.phase)'."
    }
    $startedAt = [DateTimeOffset] $state.started_at_utc
    if ($timestampUtc -lt $startedAt) {
        throw 'Return timestamp cannot be earlier than dispatch timestamp.'
    }
    $durationMilliseconds = [long] [Math]::Round(($timestampUtc - $startedAt).TotalMilliseconds)
    $tokenUsage = [ordered]@{
        input = if ($InputTokens -lt 0) { $null } else { $InputTokens }
        cached_input = if ($CachedInputTokens -lt 0) { $null } else { $CachedInputTokens }
        output = if ($OutputTokens -lt 0) { $null } else { $OutputTokens }
        reasoning = if ($ReasoningTokens -lt 0) { $null } else { $ReasoningTokens }
        total = if ($TotalTokens -lt 0) { $null } else { $TotalTokens }
    }
    $record = [ordered]@{
        timestamp_utc = $timestampUtc.ToString('O')
        event = 'AssignmentReturn'
        assignment_id = $AssignmentId
        agent_id = $state.agent_id
        role = $state.role
        phase = $state.phase
        started_at_utc = $startedAt.ToUniversalTime().ToString('O')
        ended_at_utc = $timestampUtc.ToString('O')
        duration_ms = $durationMilliseconds
        task_summary = $state.task_summary
        status = $Status
        result_summary = $normalizedSummary
        token_usage = $tokenUsage
    }
    Write-AppendLine $machineLogPath ($record | ConvertTo-Json -Compress -Depth 4)

    $displayRole = (Get-Culture).TextInfo.ToTitleCase(([string] $state.role).ToLowerInvariant())
    $startLocal = $startedAt.ToLocalTime().ToString('dd-MM-yy HH:mm', [System.Globalization.CultureInfo]::InvariantCulture)
    $duration = Format-Duration $durationMilliseconds
    $tokens = 'input={0}, cached_input={1}, output={2}, reasoning={3}, total={4}' -f
        (Format-TokenValue $InputTokens),
        (Format-TokenValue $CachedInputTokens),
        (Format-TokenValue $OutputTokens),
        (Format-TokenValue $ReasoningTokens),
        (Format-TokenValue $TotalTokens)
    $readableLine = "$startLocal | Architect -> ${displayRole}: $($state.task_summary) | ${displayRole} -> Architect: STATUS: $Status; $normalizedSummary | phase=$($state.phase) | duration=$duration | tokens: $tokens"
    Write-AppendLine $readableLogPath $readableLine

    $state.completed = $true
    $state | Add-Member -NotePropertyName completed_at_utc -NotePropertyValue $timestampUtc.ToString('O')
    [System.IO.File]::WriteAllText(
        $statePath,
        ($state | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
    exit 0
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
