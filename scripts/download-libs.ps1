$ErrorActionPreference = "Stop"

$FlatLafVersion  = "3.4.1"
$JacksonVersion  = "2.15.2"
$JsvgVersion     = "1.7.2"
$JUnitVersion    = "1.10.2"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir    = Resolve-Path (Join-Path $ScriptDir "..")
$LibDir     = Join-Path $RootDir "lib"
$TestLibDir = Join-Path $RootDir "lib/test"

New-Item -ItemType Directory -Force $LibDir     | Out-Null
New-Item -ItemType Directory -Force $TestLibDir | Out-Null

$Maven = "https://repo1.maven.org/maven2"

$Dependencies = @(
    @{
        File      = "flatlaf-$FlatLafVersion.jar"
        GroupPath = "com/formdev/flatlaf/$FlatLafVersion"
        Dir       = $LibDir
    },
    @{
        File      = "flatlaf-extras-$FlatLafVersion.jar"
        GroupPath = "com/formdev/flatlaf-extras/$FlatLafVersion"
        Dir       = $LibDir
    },
    @{
        File      = "jackson-annotations-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-annotations/$JacksonVersion"
        Dir       = $LibDir
    },
    @{
        File      = "jackson-core-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-core/$JacksonVersion"
        Dir       = $LibDir
    },
    @{
        File      = "jackson-databind-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/core/jackson-databind/$JacksonVersion"
        Dir       = $LibDir
    },
    @{
        File      = "jackson-datatype-jsr310-$JacksonVersion.jar"
        GroupPath = "com/fasterxml/jackson/datatype/jackson-datatype-jsr310/$JacksonVersion"
        Dir       = $LibDir
    },
    @{
        File      = "jsvg-$JsvgVersion.jar"
        GroupPath = "com/github/weisj/jsvg/$JsvgVersion"
        Dir       = $LibDir
    },
    @{
        File      = "junit-platform-console-standalone-$JUnitVersion.jar"
        GroupPath = "org/junit/platform/junit-platform-console-standalone/$JUnitVersion"
        Dir       = $TestLibDir
    }
)

foreach ($Dep in $Dependencies) {
    $TargetPath = Join-Path $Dep.Dir $Dep.File

    if (Test-Path $TargetPath) {
        Write-Host "Already exists: $($Dep.File)"
        continue
    }

    $Url = "$Maven/$($Dep.GroupPath)/$($Dep.File)"
    Write-Host "Downloading $($Dep.File)..."
    Invoke-WebRequest -Uri $Url -OutFile $TargetPath
}

Write-Host "Done."
Write-Host "  Runtime libs : $LibDir"
Write-Host "  Test libs    : $TestLibDir"
