param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [Parameter(Mandatory = $true)][string]$ResourceDir,
    [Parameter(Mandatory = $true)][string]$LibJar,
    [Parameter(Mandatory = $true)][string]$OutputJar
)

function Resolve-JdkTool {
    param([string]$ToolName)

    $preferredRoots = @(
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin",
        "C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot\bin"
    )

    foreach ($root in $preferredRoots) {
        $candidate = Join-Path $root $ToolName
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Unable to resolve $ToolName. Install a Windows JDK 8 or put $ToolName on PATH."
}

$javacExe = Resolve-JdkTool -ToolName "javac.exe"
$jarExe = Resolve-JdkTool -ToolName "jar.exe"
$outputDir = Split-Path -Parent $OutputJar
$buildRoot = Join-Path $env:TEMP "fight-caves-demo-client-build"
$classesDir = Join-Path $buildRoot "classes"
$sourcesList = Join-Path $buildRoot "sources.txt"
$manifestPath = Join-Path $buildRoot "manifest.mf"

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
Remove-Item -Recurse -Force $buildRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

Get-ChildItem $SourceDir -Filter '*.java' | Select-Object -ExpandProperty FullName | Set-Content -Encoding ASCII $sourcesList
Set-Content -Path $manifestPath -Value @("Main-Class: Loader", "") -Encoding ASCII

$javacArgs = @(
    '-cp', ($LibJar + ';' + $ResourceDir),
    '-d', $classesDir,
    ('@' + $sourcesList)
)
$javacProcess = Start-Process -FilePath $javacExe -ArgumentList $javacArgs -Wait -NoNewWindow -PassThru
if ($javacProcess.ExitCode -ne 0) {
    throw "javac failed with exit code $($javacProcess.ExitCode)."
}

Copy-Item (Join-Path $ResourceDir '*') $classesDir -Recurse -Force

$extractArgs = @('xf', $LibJar)
$extractProcess = Start-Process -FilePath $jarExe -WorkingDirectory $classesDir -ArgumentList $extractArgs -Wait -NoNewWindow -PassThru
if ($extractProcess.ExitCode -ne 0) {
    throw "jar extraction failed with exit code $($extractProcess.ExitCode)."
}

$jarArgs = @('cfm', $OutputJar, $manifestPath, '-C', $classesDir, '.')
$jarProcess = Start-Process -FilePath $jarExe -ArgumentList $jarArgs -Wait -NoNewWindow -PassThru
if ($jarProcess.ExitCode -ne 0) {
    throw "jar failed with exit code $($jarProcess.ExitCode)."
}

[pscustomobject]@{
    output_jar = $OutputJar
    javac_exe = $javacExe
    jar_exe = $jarExe
    source_dir = $SourceDir
    resource_dir = $ResourceDir
    lib_jar = $LibJar
} | ConvertTo-Json -Compress
