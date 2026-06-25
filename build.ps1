$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
    $mvnPath = $mvn.Source
} else {
    $candidates = @(
        "$env:MAVEN_HOME\bin\mvn.cmd",
        "C:\Program Files\Apache\Maven\bin\mvn.cmd",
        (Join-Path $projectRoot ".tools\apache-maven-3.9.6\bin\mvn.cmd")
    )
    $mvnPath = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

if (-not $mvnPath) {
    Write-Error @"
Maven was not found. Install Maven 3.9+ and ensure 'mvn' is on PATH, then run:

  mvn clean test
  mvn package
  java -jar target/vaultdb-1.0.0.jar

Download: https://maven.apache.org/download.cgi
"@
}

$goal = if ($args.Count -eq 0) { @("clean", "test") } else { $args }
& $mvnPath @goal
exit $LASTEXITCODE
