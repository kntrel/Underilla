param([string]$DependencyCache = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1")
$ErrorActionPreference = 'Stop'
$auditRepo = (Resolve-Path "$PSScriptRoot/../..").Path
$auditOutput = Join-Path $auditRepo 'build/core-performance-audit'
New-Item -ItemType Directory -Force -Path $auditOutput | Out-Null
function Find-AuditJar([string]$relativeDirectory, [string]$name) {
    $found = Get-ChildItem -LiteralPath (Join-Path $DependencyCache $relativeDirectory) -Recurse -Filter $name |
        Select-Object -First 1
    if ($null -eq $found) { throw "Missing cached dependency: $relativeDirectory/$name" }
    return $found.FullName
}
$auditNbt = Find-AuditJar 'com.github.HydrolienF/KntNBT/2.2.2' 'KntNBT-2.2.2.jar'
# This local audit uses the available compatible SLF4J API, not Gradle resolution.
$auditSlf4j = Find-AuditJar 'org.slf4j/slf4j-api/2.0.17' 'slf4j-api-2.0.17.jar'
Copy-Item -LiteralPath $auditNbt -Destination "$auditOutput/KntNBT-2.2.2.jar" -Force
Copy-Item -LiteralPath $auditSlf4j -Destination "$auditOutput/slf4j-api-2.0.17.jar" -Force
$auditClasspath = "$auditOutput/KntNBT-2.2.2.jar;$auditOutput/slf4j-api-2.0.17.jar"
$auditSources = @(Get-ChildItem -LiteralPath "$auditRepo/underilla-core/src/main/java" -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })
$auditSources += @('TestBlock.java', 'TestBiome.java') | ForEach-Object {
    "$auditRepo/underilla-core/src/test/java/com/kntrel/mc/underilla/core/impl/$_"
}
$auditSources += "$PSScriptRoot/SurfaceMaskProbe.java"
& javac -cp $auditClasspath -d $auditOutput @auditSources
if ($LASTEXITCODE -ne 0) { throw 'Audit compilation failed' }
& java -cp "$auditOutput;$auditClasspath" SurfaceMaskProbe |
    Tee-Object -FilePath "$auditOutput/probe-output.txt"
if ($LASTEXITCODE -ne 0) { throw 'Audit probe failed' }
