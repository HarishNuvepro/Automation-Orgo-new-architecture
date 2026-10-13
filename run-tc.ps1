<#
  Run a single test case (or any tag expression) with a visible browser by default.

  Usage (from the project folder):
    .\run-tc.ps1 -Tags "@login and @TC1"
    .\run-tc.ps1 -Tags "@users and @TC54" -Profile users
    .\run-tc.ps1 -Tags "@login and @TC1" -Headless      # headless / CI mode
#>
param(
    [string]$Tags = "@login and @TC1",
    [string]$Profile = "login",
    [switch]$Headless
)

Set-Location $PSScriptRoot

# Use JDK only if JAVA_HOME isn't already set to a JDK
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$headlessValue = if ($Headless) { "true" } else { "false" }

mvn test "-P$Profile" "-Dcucumber.filter.tags=$Tags" "-Dtest.headless=$headlessValue"
