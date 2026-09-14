[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Snapshot', 'Verify')]
    [string] $Command,

    [Parameter(Mandatory)]
    [string] $RepositoryRoot,

    [Parameter(Mandatory)]
    [string] $ManifestPath,

    [string[]] $AllowPath = @()
)

$ErrorActionPreference = 'Stop'
$script:PathComparison = if ([System.OperatingSystem]::IsWindows()) {
    [System.StringComparison]::OrdinalIgnoreCase
}
else {
    [System.StringComparison]::Ordinal
}

function Test-ExcludedDirectory {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $Root
    )

    $parent = [System.IO.Path]::GetDirectoryName($Path)
    if (-not $parent.Equals($Root, $script:PathComparison)) {
        return $false
    }

    $name = [System.IO.Path]::GetFileName($Path)
    return $name.Equals('.git', $script:PathComparison) -or
        $name.Equals('target', $script:PathComparison) -or
        $name.Equals('.codex-logs', $script:PathComparison)
}

function Get-FileSha256 {
    param([Parameter(Mandatory)][string] $Path)

    $stream = [System.IO.FileStream]::new(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read
    )
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.Convert]::ToHexString($algorithm.ComputeHash($stream)).ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

function Get-ReparsePointSha256 {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][bool] $IsDirectory
    )

    $fileSystemInfo = if ($IsDirectory) {
        [System.IO.DirectoryInfo]::new($Path)
    }
    else {
        [System.IO.FileInfo]::new($Path)
    }
    $kind = if ($IsDirectory) { 'directory' } else { 'file' }
    $description = "reparse-point`0$kind`0$($fileSystemInfo.LinkTarget)"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($description)
    return [System.Convert]::ToHexString(
        [System.Security.Cryptography.SHA256]::HashData($bytes)
    ).ToLowerInvariant()
}

function Get-RepositoryEntries {
    param([Parameter(Mandatory)][string] $Root)

    $entriesByPath = [System.Collections.Generic.Dictionary[string, string]]::new(
        [System.StringComparer]::Ordinal
    )
    $pendingDirectories = [System.Collections.Generic.Stack[string]]::new()
    $pendingDirectories.Push($Root)

    while ($pendingDirectories.Count -gt 0) {
        $directory = $pendingDirectories.Pop()
        foreach ($item in [System.IO.Directory]::EnumerateFileSystemEntries($directory)) {
            $attributes = [System.IO.File]::GetAttributes($item)
            $isDirectory = ($attributes -band [System.IO.FileAttributes]::Directory) -ne 0

            if ($isDirectory) {
                if (Test-ExcludedDirectory $item $Root) {
                    continue
                }

                if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                    $relativePath = [System.IO.Path]::GetRelativePath($Root, $item).Replace('\', '/')
                    $entriesByPath.Add($relativePath, (Get-ReparsePointSha256 $item $true))
                    continue
                }

                $pendingDirectories.Push($item)
                continue
            }

            $relativePath = [System.IO.Path]::GetRelativePath($Root, $item).Replace('\', '/')
            if ($relativePath.Equals('.git', $script:PathComparison)) {
                continue
            }

            $hash = if (($attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                Get-ReparsePointSha256 $item $false
            }
            else {
                Get-FileSha256 $item
            }
            $entriesByPath.Add($relativePath, $hash)
        }
    }

    return $entriesByPath
}

function ConvertTo-AllowRegex {
    param([Parameter(Mandatory)][string] $Pattern)

    if ([string]::IsNullOrWhiteSpace($Pattern) -or [System.IO.Path]::IsPathRooted($Pattern)) {
        throw "AllowPath must be a non-empty repository-relative glob: $Pattern"
    }

    $normalized = $Pattern.Replace('\', '/')
    while ($normalized.StartsWith('./', [System.StringComparison]::Ordinal)) {
        $normalized = $normalized.Substring(2)
    }

    $segments = $normalized.Split('/')
    if ($normalized.Length -eq 0 -or $segments.Contains('..')) {
        throw "AllowPath must not escape RepositoryRoot: $Pattern"
    }

    $expression = [System.Text.StringBuilder]::new('^')
    for ($index = 0; $index -lt $normalized.Length; $index++) {
        $character = $normalized[$index]
        if ($character -eq '*') {
            if ($index + 1 -lt $normalized.Length -and $normalized[$index + 1] -eq '*') {
                [void] $expression.Append('.*')
                $index++
            }
            else {
                [void] $expression.Append('[^/]*')
            }
        }
        elseif ($character -eq '?') {
            [void] $expression.Append('[^/]')
        }
        else {
            [void] $expression.Append([System.Text.RegularExpressions.Regex]::Escape($character.ToString()))
        }
    }
    [void] $expression.Append('$')

    return [System.Text.RegularExpressions.Regex]::new(
        $expression.ToString(),
        [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
}

function Test-AllowedPath {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Patterns
    )

    foreach ($pattern in $Patterns) {
        if ($pattern.IsMatch($Path)) {
            return $true
        }
    }
    return $false
}

function Assert-ManifestEntry {
    param([Parameter(Mandatory)] $Entry)

    if ($null -eq $Entry.path -or $null -eq $Entry.sha256) {
        throw 'Manifest contains an entry without path or sha256.'
    }

    $path = [string] $Entry.path
    $hash = [string] $Entry.sha256
    if (
        [string]::IsNullOrWhiteSpace($path) -or
        [System.IO.Path]::IsPathRooted($path) -or
        $path.Contains('\') -or
        $path.Split('/').Contains('..')
    ) {
        throw "Manifest contains an invalid repository-relative path: $path"
    }
    if ($hash -cnotmatch '^[0-9a-f]{64}$') {
        throw "Manifest contains an invalid SHA-256 hash for path: $path"
    }
}

try {
    $repositoryFullPath = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $manifestFullPath = [System.IO.Path]::GetFullPath($ManifestPath)

    if (-not [System.IO.Directory]::Exists($repositoryFullPath)) {
        throw "RepositoryRoot does not identify an existing directory: $repositoryFullPath"
    }

    $repositoryPrefix = $repositoryFullPath + [System.IO.Path]::DirectorySeparatorChar
    $manifestInsideRepository =
        $manifestFullPath.Equals($repositoryFullPath, $script:PathComparison) -or
        $manifestFullPath.StartsWith($repositoryPrefix, $script:PathComparison)

    if ($manifestInsideRepository) {
        throw 'ManifestPath must resolve outside RepositoryRoot.'
    }

    if ($Command -eq 'Snapshot') {
        $entriesByPath = Get-RepositoryEntries $repositoryFullPath
        $paths = [string[]] $entriesByPath.Keys
        [System.Array]::Sort($paths, [System.StringComparer]::Ordinal)

        $entries = [System.Collections.Generic.List[object]]::new()
        foreach ($path in $paths) {
            $entries.Add([ordered]@{
                path = $path
                sha256 = $entriesByPath[$path]
            })
        }

        $manifest = [ordered]@{
            version = 1
            algorithm = 'SHA-256'
            entries = $entries
        }
        $manifestJson = ($manifest | ConvertTo-Json -Depth 4 -Compress) + [Environment]::NewLine
        [System.IO.File]::WriteAllText(
            $manifestFullPath,
            $manifestJson,
            [System.Text.UTF8Encoding]::new($false)
        )
        exit 0
    }

    if (-not [System.IO.File]::Exists($manifestFullPath)) {
        throw "ManifestPath does not identify an existing file: $manifestFullPath"
    }

    $storedManifest = Get-Content -LiteralPath $manifestFullPath -Raw | ConvertFrom-Json
    if ($storedManifest.version -ne 1 -or $storedManifest.algorithm -cne 'SHA-256') {
        throw 'Manifest has an unsupported version or hashing algorithm.'
    }

    $baselineByPath = [System.Collections.Generic.Dictionary[string, string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($entry in @($storedManifest.entries)) {
        Assert-ManifestEntry $entry
        if ($baselineByPath.ContainsKey([string] $entry.path)) {
            throw "Manifest contains a duplicate path: $($entry.path)"
        }
        $baselineByPath.Add([string] $entry.path, [string] $entry.sha256)
    }

    $currentByPath = Get-RepositoryEntries $repositoryFullPath
    $baselinePaths = [string[]] $baselineByPath.Keys
    $currentPaths = [string[]] $currentByPath.Keys
    [System.Array]::Sort($baselinePaths, [System.StringComparer]::Ordinal)
    [System.Array]::Sort($currentPaths, [System.StringComparer]::Ordinal)

    $allowPatterns = [System.Collections.Generic.List[object]]::new()
    foreach ($pattern in $AllowPath) {
        $allowPatterns.Add((ConvertTo-AllowRegex $pattern))
    }

    $violations = [System.Collections.Generic.List[string]]::new()
    $baselineIndex = 0
    $currentIndex = 0
    while ($baselineIndex -lt $baselinePaths.Length -or $currentIndex -lt $currentPaths.Length) {
        if ($baselineIndex -ge $baselinePaths.Length) {
            $path = $currentPaths[$currentIndex]
            $kind = 'ADDED'
            $currentIndex++
        }
        elseif ($currentIndex -ge $currentPaths.Length) {
            $path = $baselinePaths[$baselineIndex]
            $kind = 'DELETED'
            $baselineIndex++
        }
        else {
            $comparison = [System.StringComparer]::Ordinal.Compare(
                $baselinePaths[$baselineIndex],
                $currentPaths[$currentIndex]
            )
            if ($comparison -lt 0) {
                $path = $baselinePaths[$baselineIndex]
                $kind = 'DELETED'
                $baselineIndex++
            }
            elseif ($comparison -gt 0) {
                $path = $currentPaths[$currentIndex]
                $kind = 'ADDED'
                $currentIndex++
            }
            else {
                $path = $baselinePaths[$baselineIndex]
                $baselineIndex++
                $currentIndex++
                if ($baselineByPath[$path] -ceq $currentByPath[$path]) {
                    continue
                }
                $kind = 'MODIFIED'
            }
        }

        if (-not (Test-AllowedPath $path $allowPatterns.ToArray())) {
            $violations.Add("$kind $path")
        }
    }

    if ($violations.Count -gt 0) {
        foreach ($violation in $violations) {
            [Console]::Error.WriteLine($violation)
        }
        exit 3
    }

    exit 0
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
