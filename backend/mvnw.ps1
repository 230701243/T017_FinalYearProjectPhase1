# mvnw.ps1 - Maven Wrapper for Windows PowerShell
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$MavenVersion = "3.9.6"
$MavenDirName = "apache-maven-$MavenVersion"
$DistsDir     = Join-Path $PSScriptRoot ".mvn\wrapper\dists"
$MavenHome    = Join-Path $DistsDir $MavenDirName
$MavenExe     = Join-Path $MavenHome "bin\mvn.cmd"
$DownloadUrl  = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/$MavenDirName-bin.zip"

if (-not (Test-Path $MavenExe)) {
    Write-Host "[mvnw] Apache Maven $MavenVersion not found. Downloading..." -ForegroundColor Cyan
    if (-not (Test-Path $DistsDir)) {
        New-Item -ItemType Directory -Path $DistsDir -Force | Out-Null
    }

    $ZipPath = Join-Path $DistsDir "$MavenDirName-bin.zip"
    Write-Host "[mvnw] Downloading from Maven Central..." -ForegroundColor Gray
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath -UseBasicParsing

    Write-Host "[mvnw] Extracting..." -ForegroundColor Cyan
    Expand-Archive -Path $ZipPath -DestinationPath $DistsDir -Force
    Remove-Item $ZipPath -Force

    if (-not (Test-Path $MavenExe)) {
        Write-Error "[mvnw] Extraction failed - mvn.cmd not found at expected path."
        exit 1
    }
    Write-Host "[mvnw] Maven $MavenVersion is ready." -ForegroundColor Green
    Write-Host ""
}

# Auto-detect JAVA_HOME if not set — prefer Android Studio JBR (Java 21)
if (-not $env:JAVA_HOME) {
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*"
    )
    foreach ($c in $candidates) {
        $resolved = Resolve-Path $c -ErrorAction SilentlyContinue
        if ($resolved -and (Test-Path "$resolved\bin\javac.exe")) {
            $env:JAVA_HOME = $resolved.Path
            Write-Host "[mvnw] Auto-detected JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Cyan
            break
        }
    }
}

Write-Host "[mvnw] Running: $MavenExe $MavenArgs" -ForegroundColor Gray
Write-Host ""
& $MavenExe @MavenArgs
exit $LASTEXITCODE
