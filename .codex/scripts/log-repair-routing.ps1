[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $Loop,

    [Parameter(Mandatory)]
    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $SourceStatus,

    [Parameter(Mandatory)]
    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $RepairOwner,

    [Parameter(Mandatory)]
    [ValidateRange(1, 3)]
    [int] $Round,

    [ValidateScript({ -not [string]::IsNullOrWhiteSpace($_) })]
    [string] $LogPath = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) '.codex-logs/repair-routings.jsonl')
)

$ErrorActionPreference = 'Stop'

try {
    $logFullPath = [System.IO.Path]::GetFullPath($LogPath)
    $logDirectory = [System.IO.Path]::GetDirectoryName($logFullPath)
    if ([string]::IsNullOrWhiteSpace($logDirectory)) {
        throw "LogPath does not have a parent directory: $logFullPath"
    }
    [System.IO.Directory]::CreateDirectory($logDirectory) | Out-Null

    $timestampUtc = ([System.DateTimeOffset]::UtcNow).ToString('R', [System.Globalization.CultureInfo]::InvariantCulture)
    $record = [ordered]@{
        timestamp_utc = $timestampUtc
        loop = $Loop
        source_status = $SourceStatus
        repair_owner = $RepairOwner
        round = $Round
    }
    $jsonLine = ($record | ConvertTo-Json -Compress) + [Environment]::NewLine
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($jsonLine)

    $maximumAttempts = 100
    $retryDelayMilliseconds = 20
    for ($attempt = 1; $attempt -le $maximumAttempts; $attempt++) {
        $stream = $null
        try {
            $stream = [System.IO.FileStream]::new(
                $logFullPath,
                [System.IO.FileMode]::OpenOrCreate,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::None
            )
            [void] $stream.Seek(0, [System.IO.SeekOrigin]::End)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
            exit 0
        }
        catch [System.IO.IOException] {
            if ($attempt -eq $maximumAttempts) {
                throw
            }
            Start-Sleep -Milliseconds $retryDelayMilliseconds
        }
        finally {
            if ($null -ne $stream) {
                $stream.Dispose()
            }
        }
    }
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
