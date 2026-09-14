<#
.SYNOPSIS
Runs the independent repository test-integrity preflight.

.DESCRIPTION
Inspects Java test sources, Maven configuration and the quality-gate workflow
before Maven starts. Exit code 0 reports a clean scan, 3 reports recognized
violations, and 2 reports invalid input or a scan failure.

.PARAMETER RepositoryRoot
Repository root to inspect. Defaults to the current directory.
#>
[CmdletBinding()]
param(
    [string] $RepositoryRoot = '.'
)

$ErrorActionPreference = 'Stop'

function Add-Violation {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.HashSet[string]] $Violations,
        [Parameter(Mandatory)][string] $Message
    )

    [void] $Violations.Add($Message)
}

function Mask-JavaCommentsAndLiterals {
    param([Parameter(Mandatory)][string] $Source)

    $masked = [System.Text.StringBuilder]::new($Source.Length)
    $state = 'CODE'
    $index = 0
    while ($index -lt $Source.Length) {
        $current = $Source[$index]
        $next = if ($index + 1 -lt $Source.Length) { $Source[$index + 1] } else { [char] 0 }
        $third = if ($index + 2 -lt $Source.Length) { $Source[$index + 2] } else { [char] 0 }

        if ($state -eq 'CODE') {
            if ($current -eq '/' -and $next -eq '/') {
                [void] $masked.Append('  ')
                $index += 2
                $state = 'LINE_COMMENT'
            }
            elseif ($current -eq '/' -and $next -eq '*') {
                [void] $masked.Append('  ')
                $index += 2
                $state = 'BLOCK_COMMENT'
            }
            elseif ($current -eq '"' -and $next -eq '"' -and $third -eq '"') {
                [void] $masked.Append('   ')
                $index += 3
                $state = 'TEXT_BLOCK'
            }
            elseif ($current -eq '"') {
                [void] $masked.Append(' ')
                $index++
                $state = 'STRING'
            }
            elseif ($current -eq "'") {
                [void] $masked.Append(' ')
                $index++
                $state = 'CHARACTER'
            }
            else {
                [void] $masked.Append($current)
                $index++
            }
            continue
        }

        if ($state -eq 'LINE_COMMENT') {
            if ($current -eq [char] 10 -or $current -eq [char] 13) {
                [void] $masked.Append($current)
                $state = 'CODE'
            }
            else {
                [void] $masked.Append(' ')
            }
            $index++
            continue
        }

        if ($state -eq 'BLOCK_COMMENT') {
            if ($current -eq '*' -and $next -eq '/') {
                [void] $masked.Append('  ')
                $index += 2
                $state = 'CODE'
            }
            else {
                [void] $masked.Append($(if ($current -eq [char] 10 -or $current -eq [char] 13) {
                    $current
                }
                else {
                    ' '
                }))
                $index++
            }
            continue
        }

        if ($state -eq 'TEXT_BLOCK') {
            if ($current -eq '"' -and $next -eq '"' -and $third -eq '"') {
                [void] $masked.Append('   ')
                $index += 3
                $state = 'CODE'
            }
            else {
                [void] $masked.Append($(if ($current -eq [char] 10 -or $current -eq [char] 13) {
                    $current
                }
                else {
                    ' '
                }))
                $index++
            }
            continue
        }

        $terminator = if ($state -eq 'STRING') { '"' } else { "'" }
        if ($current -eq '\' -and $index + 1 -lt $Source.Length) {
            [void] $masked.Append('  ')
            $index += 2
        }
        else {
            [void] $masked.Append($(if ($current -eq [char] 10 -or $current -eq [char] 13) {
                $current
            }
            else {
                ' '
            }))
            $index++
            if ($current -eq $terminator) {
                $state = 'CODE'
            }
        }
    }

    return $masked.ToString()
}

function Test-JavaSources {
    param(
        [Parameter(Mandatory)][string] $Root,
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.HashSet[string]] $Violations
    )

    $testRoot = [System.IO.Path]::Combine($Root, 'src', 'test')
    if (-not [System.IO.Directory]::Exists($testRoot)) {
        return
    }

    $disabledPattern = '@\s*(?:(?:org\.junit(?:\.jupiter\.api)?|junit\.framework)\.)?(?:Disabled|Ignore)\b'
    $assumptionPattern =
        '\b(?:[A-Za-z_$][\w$]*\s*\.\s*)?assumeTrue\s*\(\s*(?:\(\s*)*false\b' +
        '|\b(?:[A-Za-z_$][\w$]*\s*\.\s*)?assumeFalse\s*\(\s*(?:\(\s*)*true\b'

    foreach ($path in [System.IO.Directory]::EnumerateFiles(
            $testRoot, '*.java', [System.IO.SearchOption]::AllDirectories)) {
        $relativePath = [System.IO.Path]::GetRelativePath($Root, $path).Replace('\', '/')
        $code = Mask-JavaCommentsAndLiterals ([System.IO.File]::ReadAllText($path))
        if ([System.Text.RegularExpressions.Regex]::IsMatch($code, $disabledPattern)) {
            Add-Violation $Violations "$relativePath explicitly disables or ignores a JUnit test"
        }
        if ([System.Text.RegularExpressions.Regex]::IsMatch($code, $assumptionPattern)) {
            Add-Violation $Violations "$relativePath contains an unconditional literal false assumption"
        }
    }
}

function Read-SafeXml {
    param([Parameter(Mandatory)][string] $Path)

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create($Path, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        return $document
    }
    finally {
        $reader.Dispose()
    }
}

function Test-Pom {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.HashSet[string]] $Violations
    )

    $document = Read-SafeXml $Path
    if ($null -eq $document.DocumentElement -or $document.DocumentElement.LocalName -cne 'project') {
        throw 'pom.xml does not contain a Maven project root element.'
    }

    $skipNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($name in @('skip', 'skipTests', 'skipITs', 'maven.test.skip')) {
        [void] $skipNames.Add($name)
    }
    $selectorNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($name in @('includes', 'include', 'excludes', 'exclude', 'groups', 'excludedGroups',
            'includeTags', 'excludeTags', 'test', 'itTest', 'it.test', 'suiteXmlFiles')) {
        [void] $selectorNames.Add($name)
    }

    $propertyXPath =
        "/*[local-name()='project']/*[local-name()='properties']/*" +
        " | /*[local-name()='project']/*[local-name()='profiles']/*[local-name()='profile']" +
        "/*[local-name()='properties']/*"
    foreach ($property in @($document.SelectNodes($propertyXPath))) {
        $name = $property.LocalName
        $value = $property.InnerText.Trim()
        if ($skipNames.Contains($name) -and
                -not $value.Equals('false', [System.StringComparison]::OrdinalIgnoreCase)) {
            Add-Violation $Violations "pom.xml Maven property narrows test execution: $name"
        }
        elseif ($selectorNames.Contains($name) -and -not [string]::IsNullOrWhiteSpace($value)) {
            Add-Violation $Violations "pom.xml Maven property selects part of the test suite: $name"
        }
    }

    foreach ($plugin in @($document.SelectNodes("//*[local-name()='plugin']"))) {
        $artifactIdNode = $plugin.SelectSingleNode("./*[local-name()='artifactId']")
        if ($null -eq $artifactIdNode) {
            continue
        }
        $artifactId = $artifactIdNode.InnerText.Trim()
        if ($artifactId -cne 'maven-surefire-plugin' -and $artifactId -cne 'maven-failsafe-plugin') {
            continue
        }

        $configurationXPath =
            "./*[local-name()='configuration']" +
            " | ./*[local-name()='executions']/*[local-name()='execution']/*[local-name()='configuration']"
        foreach ($configuration in @($plugin.SelectNodes($configurationXPath))) {
            foreach ($element in @($configuration.SelectNodes('.//*'))) {
                $name = $element.LocalName
                $value = $element.InnerText.Trim()
                if ($skipNames.Contains($name) -and
                        -not $value.Equals('false', [System.StringComparison]::OrdinalIgnoreCase)) {
                    Add-Violation $Violations "pom.xml $artifactId skips test execution with $name"
                }
                elseif ($selectorNames.Contains($name) -and -not [string]::IsNullOrWhiteSpace($value)) {
                    Add-Violation $Violations "pom.xml $artifactId selects part of the test suite with $name"
                }
            }
        }
    }
}

function Get-MavenArgumentViolations {
    param([Parameter(Mandatory)][string] $Content)

    $withoutComments = [System.Text.RegularExpressions.Regex]::Replace($Content, '(?m)#.*$', '')
    $argumentPattern = [System.Text.RegularExpressions.Regex]::new(@'
(?ix)(?:^|[\s"'])(?:
    -D(?<short_name>[A-Za-z0-9_.-]+)(?:=(?<short_value>[^\s"'#]+))?
  | --define(?:=|\s+)["']?(?<long_name>[A-Za-z0-9_.-]+)(?:=(?<long_value>[^\s"'#]+))?
)
'@)
    $messages = [System.Collections.Generic.List[string]]::new()
    foreach ($match in $argumentPattern.Matches($withoutComments)) {
        $shortForm = $match.Groups['short_name'].Success
        $nameGroup = if ($shortForm) { $match.Groups['short_name'] } else { $match.Groups['long_name'] }
        $valueGroup = if ($shortForm) { $match.Groups['short_value'] } else { $match.Groups['long_value'] }
        $name = $nameGroup.Value.ToLowerInvariant()
        $value = if ($valueGroup.Success) { $valueGroup.Value } else { '' }
        $isSkip = @('maven.test.skip', 'skiptests', 'skipits') -contains $name
        $isSelector = @(
            'test', 'it.test', 'groups', 'excludedgroups', 'includetags', 'excludetags',
            'surefire.includes', 'surefire.excludes', 'failsafe.includes', 'failsafe.excludes'
        ) -contains $name
        if (($isSkip -and -not $value.Equals('false', [System.StringComparison]::OrdinalIgnoreCase)) -or
                $isSelector) {
            $messages.Add($match.Value.Trim())
        }
    }
    return $messages.ToArray()
}

function Get-LeadingWhitespace {
    param([Parameter(Mandatory)][string] $Value)

    $length = 0
    while ($length -lt $Value.Length -and [char]::IsWhiteSpace($Value[$length])) {
        $length++
    }
    return $length
}

function Get-YamlMavenEnvironmentValues {
    param([Parameter(Mandatory)][string] $Content)

    $values = [System.Collections.Generic.List[string]]::new()
    $lines = [System.Text.RegularExpressions.Regex]::Split($Content, '\r?\n')
    $environmentPattern =
        [System.Text.RegularExpressions.Regex]::new('^(\s*)(?:MAVEN_ARGS|MAVEN_OPTS|MAVEN_CLI_OPTS)\s*:\s*(.*)$')
    for ($index = 0; $index -lt $lines.Length; $index++) {
        $match = $environmentPattern.Match($lines[$index])
        if (-not $match.Success) {
            continue
        }

        $baseIndent = $match.Groups[1].Value.Length
        $scalar = $match.Groups[2].Value.Trim()
        $value = [System.Text.StringBuilder]::new()
        $isBlock = $scalar -match '^[>|][+-]?$'
        if (-not [string]::IsNullOrWhiteSpace($scalar) -and -not $isBlock) {
            [void] $value.Append($scalar)
        }
        if ([string]::IsNullOrWhiteSpace($scalar) -or $isBlock) {
            while ($index + 1 -lt $lines.Length) {
                $nextLine = $lines[$index + 1]
                if (-not [string]::IsNullOrWhiteSpace($nextLine) -and
                        (Get-LeadingWhitespace $nextLine) -le $baseIndent) {
                    break
                }
                $index++
                if (-not [string]::IsNullOrWhiteSpace($nextLine)) {
                    [void] $value.Append(' ')
                    [void] $value.Append($nextLine.Trim())
                }
            }
        }
        $values.Add($value.ToString())
    }
    return $values.ToArray()
}

function Get-WorkflowStepPropertyValues {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][AllowEmptyString()][string[]] $Lines,
        [Parameter(Mandatory)][int] $RunIndex,
        [Parameter(Mandatory)][string] $PropertyName
    )

    $stepStart = -1
    $stepIndent = -1
    for ($index = $RunIndex; $index -ge 0; $index--) {
        $stepMatch = [System.Text.RegularExpressions.Regex]::Match($Lines[$index], '^(\s*)-\s+')
        if ($stepMatch.Success) {
            $stepStart = $index
            $stepIndent = $stepMatch.Groups[1].Value.Length
            break
        }
    }
    if ($stepStart -lt 0) {
        return @()
    }

    $stepEnd = $Lines.Length
    for ($index = $stepStart + 1; $index -lt $Lines.Length; $index++) {
        if ([string]::IsNullOrWhiteSpace($Lines[$index])) {
            continue
        }
        $indent = Get-LeadingWhitespace $Lines[$index]
        if ($indent -lt $stepIndent) {
            $stepEnd = $index
            break
        }
        $nextStep = [System.Text.RegularExpressions.Regex]::Match($Lines[$index], '^(\s*)-\s+')
        if ($nextStep.Success -and $nextStep.Groups[1].Value.Length -eq $stepIndent) {
            $stepEnd = $index
            break
        }
    }

    $propertyIndent = Get-LeadingWhitespace $Lines[$RunIndex]
    $propertyPattern = [System.Text.RegularExpressions.Regex]::new(
        '^(\s*)' + [System.Text.RegularExpressions.Regex]::Escape($PropertyName) + '\s*:\s*(.*)$'
    )
    $values = [System.Collections.Generic.List[string]]::new()
    for ($index = $stepStart; $index -lt $stepEnd; $index++) {
        $match = $propertyPattern.Match($Lines[$index])
        if ($match.Success -and $match.Groups[1].Value.Length -eq $propertyIndent) {
            $values.Add($match.Groups[2].Value.Trim())
        }
    }
    return $values.ToArray()
}

function Test-YamlTrue {
    param([Parameter(Mandatory)][string] $Value)

    $withoutComment = [System.Text.RegularExpressions.Regex]::Replace($Value, '\s+#.*$', '').Trim()
    $withoutQuotes = $withoutComment.Trim([char[]] @([char] 34, [char] 39))
    return $withoutQuotes.Equals('true', [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-QualityGate {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.HashSet[string]] $Violations
    )

    $content = [System.IO.File]::ReadAllText($Path)
    $lines = [System.Text.RegularExpressions.Regex]::Split($content, '\r?\n')
    $preflight = 'run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1'
    $maven = 'run: ./mvnw clean verify'
    $trimmedLines = @($lines | ForEach-Object { $_.Trim() })
    $preflightIndexes = @()
    $mavenIndexes = @()
    for ($index = 0; $index -lt $trimmedLines.Count; $index++) {
        if ($trimmedLines[$index] -ceq $preflight) {
            $preflightIndexes += $index
        }
        if ($trimmedLines[$index] -ceq $maven) {
            $mavenIndexes += $index
        }
        if ($trimmedLines[$index].StartsWith('run:', [System.StringComparison]::Ordinal) -and
                $trimmedLines[$index].Contains('mvnw', [System.StringComparison]::Ordinal) -and
                $trimmedLines[$index] -cne $maven) {
            Add-Violation $Violations (
                '.github/workflows/quality-gate.yml Maven command is not exactly ./mvnw clean verify'
            )
        }
    }
    if ($mavenIndexes.Count -ne 1) {
        Add-Violation $Violations (
            '.github/workflows/quality-gate.yml must contain exactly one complete Maven verify command'
        )
    }
    if ($preflightIndexes.Count -ne 1 -or $mavenIndexes.Count -ne 1 -or
            ($preflightIndexes.Count -eq 1 -and $mavenIndexes.Count -eq 1 -and
                $preflightIndexes[0] -gt $mavenIndexes[0])) {
        Add-Violation $Violations (
            '.github/workflows/quality-gate.yml must run verify-test-integrity.ps1 exactly once before Maven'
        )
    }

    if ($preflightIndexes.Count -eq 1) {
        $preflightConditions = @(Get-WorkflowStepPropertyValues $lines $preflightIndexes[0] 'if')
        if ($preflightConditions.Count -gt 0) {
            Add-Violation $Violations (
                '.github/workflows/quality-gate.yml preflight step must not be conditional'
            )
        }
        $continueValues = @(
            Get-WorkflowStepPropertyValues $lines $preflightIndexes[0] 'continue-on-error'
        )
        if ($continueValues | Where-Object { Test-YamlTrue $_ }) {
            Add-Violation $Violations (
                '.github/workflows/quality-gate.yml preflight step must not continue on error'
            )
        }
    }

    if ($mavenIndexes.Count -eq 1) {
        $mavenConditions = @(Get-WorkflowStepPropertyValues $lines $mavenIndexes[0] 'if')
        if ($mavenConditions.Count -gt 0) {
            Add-Violation $Violations (
                '.github/workflows/quality-gate.yml Maven step must depend on preflight success'
            )
        }
    }

    foreach ($environmentValue in @(Get-YamlMavenEnvironmentValues $content)) {
        if (@(Get-MavenArgumentViolations $environmentValue).Count -gt 0) {
            Add-Violation $Violations (
                '.github/workflows/quality-gate.yml Maven environment narrows test execution'
            )
        }
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        throw 'RepositoryRoot must not be blank.'
    }

    $repositoryFullPath = [System.IO.Path]::GetFullPath($RepositoryRoot)
    if (-not [System.IO.Directory]::Exists($repositoryFullPath)) {
        throw "RepositoryRoot does not identify an existing directory: $repositoryFullPath"
    }

    $pomPath = [System.IO.Path]::Combine($repositoryFullPath, 'pom.xml')
    $workflowPath = [System.IO.Path]::Combine(
        $repositoryFullPath, '.github', 'workflows', 'quality-gate.yml')
    if (-not [System.IO.File]::Exists($pomPath)) {
        throw "Required Maven project file does not exist: $pomPath"
    }
    if (-not [System.IO.File]::Exists($workflowPath)) {
        throw "Required quality-gate workflow does not exist: $workflowPath"
    }

    $violations = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    Test-JavaSources $repositoryFullPath $violations
    Test-Pom $pomPath $violations

    $mavenConfigPath = [System.IO.Path]::Combine($repositoryFullPath, '.mvn', 'maven.config')
    if ([System.IO.File]::Exists($mavenConfigPath)) {
        foreach ($message in @(Get-MavenArgumentViolations (
                    [System.IO.File]::ReadAllText($mavenConfigPath)))) {
            Add-Violation $violations ".mvn/maven.config narrows test execution: $message"
        }
    }
    Test-QualityGate $workflowPath $violations

    if ($violations.Count -gt 0) {
        $messages = [string[]] $violations
        [System.Array]::Sort($messages, [System.StringComparer]::Ordinal)
        foreach ($message in $messages) {
            [Console]::Error.WriteLine($message)
        }
        exit 3
    }

    exit 0
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
