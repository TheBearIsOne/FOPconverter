param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ElasticsearchUrl = "http://localhost:9200",
    [string]$KibanaUrl = "http://localhost:5601",
    [string]$OutputDir = "tmp-test-output",
    [int]$MaxPollAttempts = 60,
    [int]$PollIntervalMilliseconds = 1000
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

function Invoke-JsonGet {
    param([string]$Uri)
    return Invoke-RestMethod -Uri $Uri -Method Get
}

function Invoke-WithRetry {
    param(
        [scriptblock]$Action,
        [string]$Description,
        [int]$Attempts = 5,
        [int]$IntervalMilliseconds = 1000
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            return & $Action
        } catch {
            if ($attempt -eq $Attempts) {
                throw "${Description} failed after $Attempts attempts. $($_.Exception.Message)"
            }

            Start-Sleep -Milliseconds $IntervalMilliseconds
        }
    }
}

function Wait-ForApiHealth {
    param(
        [string]$HealthUrl,
        [int]$Attempts = 60,
        [int]$IntervalMilliseconds = 1000
    )

    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        try {
            $healthResponse = Invoke-JsonGet -Uri $HealthUrl
            if ($healthResponse.status -eq 'UP') {
                return $healthResponse
            }
        } catch {
        }

        Start-Sleep -Milliseconds $IntervalMilliseconds
    }

    throw "API health did not become UP at $HealthUrl"
}

$resolvedOutputDir = Get-AbsolutePath -Path $OutputDir
$samplesDir = Join-Path $PSScriptRoot "FopClientExample\samples"
$syncOutput = Join-Path $resolvedOutputDir "smoke-sync.pdf"
$asyncOutput = Join-Path $resolvedOutputDir "smoke-async.pdf"

New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

Write-Step "Checking API health"
$health = Wait-ForApiHealth -HealthUrl "$BaseUrl/actuator/health"

Write-Step "Running synchronous FO -> PDF conversion"
& curl.exe -sS -X POST "$BaseUrl/api/v1/convert/sync" `
    -F "payloadType=XSL_FO" `
    -F "outputFormat=PDF" `
    -F "primaryDocument=@$samplesDir/sample.fo" `
    --output "$syncOutput"

$syncFile = Get-Item $syncOutput
if ($syncFile.Length -le 0) {
    throw "Synchronous output file is empty: $syncOutput"
}

Write-Step "Submitting asynchronous XML + XSLT -> PDF conversion"
$xml = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $samplesDir "sample.xml")))
$xslt = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $samplesDir "sample.xslt")))
$body = @{
    payloadType = "XML_XSLT"
    outputFormat = "PDF"
    primaryDocumentBase64 = $xml
    primaryFileName = "sample.xml"
    secondaryDocumentBase64 = $xslt
    secondaryFileName = "sample.xslt"
} | ConvertTo-Json -Depth 5

$submit = Invoke-RestMethod -Uri "$BaseUrl/api/v1/convert" -Method Post -ContentType "application/json" -Body $body
$jobId = $submit.jobId

if ([string]::IsNullOrWhiteSpace($jobId)) {
    throw "Async submit did not return jobId: $($submit | ConvertTo-Json -Depth 10 -Compress)"
}

Write-Step "Polling async job status for $jobId"
$status = $null
for ($attempt = 0; $attempt -lt $MaxPollAttempts; $attempt++) {
    $status = Invoke-JsonGet -Uri "$BaseUrl/api/v1/jobs/$jobId"

    if ($status.status -eq "completed") {
        break
    }

    if ($status.status -eq "failed") {
        throw "Async job failed: $($status | ConvertTo-Json -Depth 10 -Compress)"
    }

    Start-Sleep -Milliseconds $PollIntervalMilliseconds
}

if ($status.status -ne "completed") {
    throw "Async job did not complete within $MaxPollAttempts polls. Last status: $($status | ConvertTo-Json -Depth 10 -Compress)"
}

Write-Step "Downloading async artifact"
Invoke-WebRequest -Uri "$BaseUrl$($status.downloadUrl)" -OutFile $asyncOutput
$asyncFile = Get-Item $asyncOutput
if ($asyncFile.Length -le 0) {
    throw "Async output file is empty: $asyncOutput"
}

Write-Step "Refreshing Elasticsearch and checking ingested log indices"
$null = Invoke-WithRetry -Description "Elasticsearch refresh" -Action {
    Invoke-WebRequest -Uri "$ElasticsearchUrl/_refresh" -Method Post
}
$logIndices = Invoke-WithRetry -Description "Elasticsearch log index lookup" -Action {
    Invoke-WebRequest -UseBasicParsing -Uri "$ElasticsearchUrl/_cat/indices/fopconverter-logs-*?h=index,docs.count"
}
$logRoleAggregation = Invoke-WithRetry -Description "Elasticsearch service role aggregation" -Action {
    Invoke-RestMethod -Method Post -Uri "$ElasticsearchUrl/fopconverter-logs-*/_search" -ContentType "application/json" -Body '{"size":0,"aggs":{"service_roles":{"terms":{"field":"service_role","size":10}}}}'
}
$serviceRoles = $logRoleAggregation.aggregations.service_roles.buckets |
    ForEach-Object { $_.key } |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Select-Object -Unique

if (-not $serviceRoles) {
    throw "No service_role field found in recent Elasticsearch log documents."
}

if ($serviceRoles -notcontains 'app' -or $serviceRoles -notcontains 'worker') {
    throw "Expected both app and worker roles in Elasticsearch logs. Actual roles: $($serviceRoles -join ', ')"
}

Write-Step "Checking Kibana data view bootstrap"
$kibanaDataView = Invoke-WithRetry -Description "Kibana data view lookup" -Action {
    Invoke-RestMethod -Method Get -Uri "$KibanaUrl/api/data_views/data_view/fopconverter-logs" -Headers @{ 'kbn-xsrf' = 'smoke-test' }
}
if ($kibanaDataView.data_view.id -ne 'fopconverter-logs') {
    throw "Kibana data view fopconverter-logs was not found."
}

[pscustomobject]@{
    healthStatus = $health.status
    syncOutput = $syncFile.FullName
    syncBytes = $syncFile.Length
    asyncJobId = $jobId
    asyncStatus = $status.status
    asyncOutput = $asyncFile.FullName
    asyncBytes = $asyncFile.Length
    logIndices = ($logIndices.Content -split "`r?`n" | Where-Object { $_ -match '\S' })
    serviceRoles = $serviceRoles
    kibanaDataViewId = $kibanaDataView.data_view.id
} | ConvertTo-Json -Depth 5