param(
    [string]$JavaExe = "",
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$Address,
    [int]$Port = 43594,
    [Parameter(Mandatory = $true)][string]$StdoutPath,
    [Parameter(Mandatory = $true)][string]$StderrPath,
    [string]$CacheSourceDir = "",
    [string]$Username = "",
    [string]$Password = "",
    [int]$LoginDelaySeconds = 4,
    [switch]$ShowBootstrap
)

function Get-PreferencesTemplate {
    return [byte[]](
        24, 0, 0, 3, 0, 1, 1, 1, 1, 1, 1,
        2, 0, 2, 2, 0, 1, 0, 1, 2, 1, 2,
        2, 1, 4, 4, 7, 0, 0, 0, 127, 0, 0, 1
    )
}

function Resolve-JavaExe {
    param([string]$ExplicitPath)

    $candidates = @()
    if ($ExplicitPath) {
        $candidates += $ExplicitPath
    }
    if ($env:FIGHT_CAVES_DEMO_JAVA_EXE) {
        $candidates += $env:FIGHT_CAVES_DEMO_JAVA_EXE
    }
    $candidates += @(
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin\javaw.exe",
        "C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot\bin\java.exe",
        "C:\Program Files\AdoptOpenJDK\jdk-8.0.292.10-hotspot\bin\javaw.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    foreach ($commandName in @("java.exe", "javaw.exe", "java", "javaw")) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "Unable to resolve a Windows Java runtime. Set -JavaExe or FIGHT_CAVES_DEMO_JAVA_EXE."
}

function Seed-ClientPreferences {
    param([byte]$ToolkitMode = 2)

    $template = Get-PreferencesTemplate
    foreach ($name in @("jagex_runescape_preferences.dat", "jagex_runescape_preferences2.dat")) {
        $path = Join-Path $env:USERPROFILE $name
        $bytes = $template.Clone()
        if (Test-Path $path) {
            $existing = [System.IO.File]::ReadAllBytes($path)
            if ($existing.Length -ge $template.Length) {
                $bytes = $existing
            }
        }
        $bytes[19] = $ToolkitMode
        [System.IO.File]::WriteAllBytes($path, $bytes)
    }
}

function Sync-ClientCache {
    param([string]$SourceDir)

    if (-not $SourceDir) {
        return @{
            source_dir = $SourceDir
            destination_dir = "C:\.jagex_cache_32\runescape"
            copied = 0
            skipped = 0
            elapsed_ms = 0
        }
    }

    if (-not (Test-Path $SourceDir)) {
        throw "RSPS cache directory not found at $SourceDir."
    }

    $destinationDir = "C:\.jagex_cache_32\runescape"
    New-Item -ItemType Directory -Path $destinationDir -Force | Out-Null

    $copied = 0
    $skipped = 0
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

    Get-ChildItem $SourceDir -Filter "main_file_cache*" | ForEach-Object {
        $destinationPath = Join-Path $destinationDir $_.Name
        $shouldCopy = $true
        if (Test-Path $destinationPath) {
            $destinationItem = Get-Item $destinationPath
            if ($destinationItem.Length -eq $_.Length) {
                $shouldCopy = $false
            }
        }
        if ($shouldCopy) {
            Copy-Item $_.FullName $destinationPath -Force
            (Get-Item $destinationPath).LastWriteTimeUtc = $_.LastWriteTimeUtc
            $copied++
        } else {
            $skipped++
        }
    }

    $stopwatch.Stop()
    return @{
        source_dir = $SourceDir
        destination_dir = $destinationDir
        copied = $copied
        skipped = $skipped
        elapsed_ms = [int]$stopwatch.ElapsedMilliseconds
    }
}

function Send-LoginKeys {
    param(
        [int]$ProcessId,
        [string]$Username,
        [string]$Password,
        [int]$DelaySeconds
    )

    if (-not $Username -or -not $Password) {
        return
    }

    Add-Type -AssemblyName Microsoft.VisualBasic
    Add-Type -AssemblyName System.Windows.Forms

    Start-Sleep -Seconds $DelaySeconds

    $activated = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        if ([Microsoft.VisualBasic.Interaction]::AppActivate($ProcessId)) {
            $activated = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }

    if (-not $activated) {
        throw "Unable to focus the client window for login autofill."
    }

    Start-Sleep -Milliseconds 750
    [System.Windows.Forms.SendKeys]::SendWait($Username)
    Start-Sleep -Milliseconds 250
    [System.Windows.Forms.SendKeys]::SendWait("{TAB}")
    Start-Sleep -Milliseconds 250
    [System.Windows.Forms.SendKeys]::SendWait($Password)
    Start-Sleep -Milliseconds 250
    [System.Windows.Forms.SendKeys]::SendWait("{ENTER}")
}

$resolvedJavaExe = Resolve-JavaExe -ExplicitPath $JavaExe
$useClientAutologin = [bool]($Username -and $Password -and ((Split-Path -Leaf $JarPath) -like "void-client-fight-caves-demo*.jar"))

$stdoutDirectory = Split-Path -Parent $StdoutPath
$stderrDirectory = Split-Path -Parent $StderrPath
if ($stdoutDirectory) {
    New-Item -ItemType Directory -Path $stdoutDirectory -Force | Out-Null
}
if ($stderrDirectory -and $stderrDirectory -ne $stdoutDirectory) {
    New-Item -ItemType Directory -Path $stderrDirectory -Force | Out-Null
}

$cacheSync = Sync-ClientCache -SourceDir $CacheSourceDir
Seed-ClientPreferences

$argumentList = @("-jar", $JarPath, "--address", $Address, "--port", "$Port")
if ($useClientAutologin) {
    $argumentList += @("--username", $Username, "--password", $Password)
    if ($ShowBootstrap) {
        $argumentList += @("--show-during-bootstrap")
    }
}

$process = Start-Process `
    -FilePath $resolvedJavaExe `
    -ArgumentList $argumentList `
    -PassThru `
    -RedirectStandardOutput $StdoutPath `
    -RedirectStandardError $StderrPath

Start-Sleep -Seconds 1

$live = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
if ($null -eq $live) {
    Write-Error "The stock client process exited before the window became available."
    exit 1
}

$summary = [pscustomobject]@{
    process_id = $live.Id
    process_name = $live.ProcessName
    main_window_title = $live.MainWindowTitle
    java_exe = $resolvedJavaExe
    jar_path = $JarPath
    address = $Address
    port = $Port
    stdout_path = $StdoutPath
    stderr_path = $StderrPath
    login_autofill = [bool]($Username -and $Password)
    client_autologin = $useClientAutologin
    bootstrap_hidden_until_ingame = [bool]($useClientAutologin -and -not $ShowBootstrap)
    preferences_seeded = $true
    preferences_path = (Join-Path $env:USERPROFILE "jagex_runescape_preferences.dat")
    cache_source_dir = $cacheSync.source_dir
    cache_destination_dir = $cacheSync.destination_dir
    cache_files_copied = $cacheSync.copied
    cache_files_skipped = $cacheSync.skipped
    cache_sync_elapsed_ms = $cacheSync.elapsed_ms
}
$summary | ConvertTo-Json -Compress

if ($Username -and $Password -and -not $useClientAutologin) {
    Send-LoginKeys -ProcessId $live.Id -Username $Username -Password $Password -DelaySeconds $LoginDelaySeconds
    Write-Output "LOGIN_KEYS_SENT"
}
