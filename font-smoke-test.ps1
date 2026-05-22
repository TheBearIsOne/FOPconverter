param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutputDir = "tmp-test-output\font-smoke",
    [string[]]$Samples = @(
        "FopClientExample/samples/sample-font-showcase.fo",
        "FopClientExample/samples/sample-cyrillic.fo",
        "FopClientExample/samples/sample-cjk.fo",
        "FopClientExample/samples/sample-rtl.fo"
    )
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Get-AbsolutePath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return Join-Path $PSScriptRoot $Path
}

function Wait-ForApiHealth {
    param(
        [string]$HealthUrl,
        [int]$Attempts = 60,
        [int]$IntervalMilliseconds = 1000
    )

    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri $HealthUrl -Method Get
            if ($health.status -eq 'UP') {
                return $health
            }
        } catch {
        }

        Start-Sleep -Milliseconds $IntervalMilliseconds
    }

    throw "API health did not become UP at $HealthUrl"
}

$resolvedOutputDir = Get-AbsolutePath -Path $OutputDir
New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

Write-Step "Checking API health"
$health = Wait-ForApiHealth -HealthUrl "$BaseUrl/actuator/health"

$results = foreach ($sample in $Samples) {
    $samplePath = Get-AbsolutePath -Path $sample
    if (-not (Test-Path $samplePath)) {
        throw "Sample file not found: $samplePath"
    }

    $outputFileName = [System.IO.Path]::GetFileNameWithoutExtension($samplePath) + ".pdf"
    $outputPath = Join-Path $resolvedOutputDir $outputFileName

    Write-Step "Rendering $sample"
    & curl.exe -sS -X POST "$BaseUrl/api/v1/convert/sync" `
        -F "payloadType=XSL_FO" `
        -F "outputFormat=PDF" `
        -F "primaryDocument=@$samplePath" `
        --output "$outputPath"

    $file = Get-Item $outputPath
    if ($file.Length -le 0) {
        throw "Rendered file is empty for sample $sample"
    }

    [pscustomobject]@{
        sample = $sample
        output = $file.FullName
        bytes = $file.Length
    }
}

[pscustomobject]@{
    healthStatus = $health.status
    outputDirectory = $resolvedOutputDir
    rendered = $results
} | ConvertTo-Json -Depth 5