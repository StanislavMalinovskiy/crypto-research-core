[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'verify-test-integrity.ps1'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('test-integrity-tests-' + [guid]::NewGuid().ToString('N'))
$failures = [System.Collections.Generic.List[string]]::new()

$cleanPom = @'
<project>
  <modelVersion>4.0.0</modelVersion>
  <build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId></plugin></plugins></build>
</project>
'@

$cleanWorkflow = @'
name: quality-gate
jobs:
  quality-gate:
    steps:
      - name: Verify test integrity
        shell: pwsh
        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1
      - name: Verify Maven build
        run: ./mvnw clean verify
'@

function Invoke-Preflight {
    param([Parameter(Mandatory)][string] $Root)

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @('-NoProfile', '-File', $scriptPath, '-RepositoryRoot', $Root)) {
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

function New-CleanRepository {
    param([Parameter(Mandatory)][string] $Name)

    $root = Join-Path $testRoot $Name
    foreach ($directory in @('src/test/java/example', '.mvn', '.github/workflows')) {
        [System.IO.Directory]::CreateDirectory((Join-Path $root $directory)) | Out-Null
    }
    [System.IO.File]::WriteAllText(
        (Join-Path $root 'src/test/java/example/SampleTest.java'),
        "class SampleTest { @Test void runs() { assumeTrue(featureAvailable()); } }`n"
    )
    [System.IO.File]::WriteAllText((Join-Path $root 'pom.xml'), $cleanPom)
    [System.IO.File]::WriteAllText((Join-Path $root '.mvn/maven.config'), "-T1C`n")
    [System.IO.File]::WriteAllText((Join-Path $root '.github/workflows/quality-gate.yml'), $cleanWorkflow)
    & git -C $root init --quiet
    & git -C $root add --all
    if ($LASTEXITCODE -ne 0) {
        throw "git fixture setup failed for $root"
    }
    return $root
}

function Assert-Clean {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)][string] $Root
    )

    Assert-ExitCode $Scenario (Invoke-Preflight $Root) 0
}

function Assert-Violation {
    param(
        [Parameter(Mandatory)][string] $Scenario,
        [Parameter(Mandatory)][string] $RelativePath,
        [Parameter(Mandatory)][string] $Content,
        [Parameter(Mandatory)][string] $ExpectedOutput
    )

    $root = New-CleanRepository $Scenario.Replace(' ', '-')
    [System.IO.File]::WriteAllText((Join-Path $root $RelativePath), $Content)
    $result = Invoke-Preflight $root
    Assert-ExitCode $Scenario $result 3
    if (-not $result.Output.Contains($ExpectedOutput, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-Failure $Scenario "expected output to identify '$ExpectedOutput'; output: $($result.Output)"
    }
}

try {
    [System.IO.Directory]::CreateDirectory($testRoot) | Out-Null

    Assert-Clean 'clean representative repository' (New-CleanRepository 'clean')
    Assert-Clean 'current repository' $repositoryRoot

    Assert-Violation 'Java Disabled annotation' 'src/test/java/example/SampleTest.java' `
        "class SampleTest { @Disabled @Test void neverRuns() {} }`n" 'SampleTest.java'
    Assert-Violation 'Java Ignore annotation' 'src/test/java/example/SampleTest.java' `
        "class SampleTest { @org.junit.Ignore @Test void neverRuns() {} }`n" 'SampleTest.java'
    Assert-Violation 'Java false assumption' 'src/test/java/example/SampleTest.java' `
        "class SampleTest { @Test void neverRuns() { assumeTrue((false)); } }`n" 'SampleTest.java'

    Assert-Violation 'POM skip property' 'pom.xml' `
        '<project><modelVersion>4.0.0</modelVersion><properties><skipTests>true</skipTests></properties></project>' `
        'pom.xml'
    Assert-Violation 'POM self-excluding Surefire' 'pom.xml' `
        '<project><modelVersion>4.0.0</modelVersion><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><configuration><excludes><exclude>**/RepositoryConventionsTest.java</exclude></excludes></configuration></plugin></plugins></build></project>' `
        'pom.xml'

    Assert-Violation 'Maven config short define' '.mvn/maven.config' "-DskipTests`n" '.mvn/maven.config'
    Assert-Violation 'Maven config long define' '.mvn/maven.config' "--define skipTests=true`n" '.mvn/maven.config'

    $plainEnvironment = $cleanWorkflow.Replace(
        '        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1',
        "        env:`n          MAVEN_ARGS: -DskipTests`n        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1"
    )
    $quotedEnvironment = $cleanWorkflow.Replace(
        '        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1',
        "        env:`n          MAVEN_ARGS: `"-DskipTests`"`n        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1"
    )
    $foldedEnvironment = $cleanWorkflow.Replace(
        '        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1',
        "        env:`n          MAVEN_ARGS: >-`n            --define skipTests=true`n        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1"
    )
    $conditionalPreflight = $cleanWorkflow.Replace(
        '        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1',
        "        if: false`n        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1"
    )
    $ignoredPreflightFailure = $cleanWorkflow.Replace(
        '        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1',
        "        continue-on-error: true`n        run: pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1"
    )
    $unconditionalMaven = $cleanWorkflow.Replace(
        '        run: ./mvnw clean verify',
        ('        if: ${{ always() }}' + "`n" + '        run: ./mvnw clean verify')
    )
    Assert-Violation 'CI plain narrowing environment' '.github/workflows/quality-gate.yml' `
        $plainEnvironment 'quality-gate.yml'
    Assert-Violation 'CI quoted narrowing environment' '.github/workflows/quality-gate.yml' `
        $quotedEnvironment 'quality-gate.yml'
    Assert-Violation 'CI folded narrowing environment' '.github/workflows/quality-gate.yml' `
        $foldedEnvironment 'quality-gate.yml'
    Assert-Violation 'CI conditional preflight' '.github/workflows/quality-gate.yml' `
        $conditionalPreflight 'quality-gate.yml'
    Assert-Violation 'CI ignored preflight failure' '.github/workflows/quality-gate.yml' `
        $ignoredPreflightFailure 'quality-gate.yml'
    Assert-Violation 'CI Maven bypass after failed preflight' '.github/workflows/quality-gate.yml' `
        $unconditionalMaven 'quality-gate.yml'
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

[Console]::Out.WriteLine('PASS: independent test-integrity preflight scenarios')
exit 0
