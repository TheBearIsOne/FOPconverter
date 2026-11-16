# FopClientExample

Console .NET 8 client for the Java-based FOP Converter service.

## Features

- sync conversion via `/api/v1/convert/sync`
- async submit + polling + download via `/api/v1/convert` using `statusUrl` and `downloadUrl` from API responses
- `IHttpClientFactory`, `async/await`, and retries for transient HTTP errors
- `CancellationToken` across the full call chain
- Serilog structured logging

## Run

```powershell
dotnet restore FopClientExample/FopClientExample.sln
dotnet run --project FopClientExample/src/FopClientExample/FopClientExample.csproj
```

## Configuration

All parameters are in `src/FopClientExample/appsettings.json`:

- `ConversionApi:BaseUrl` — URL of the Java service
- `ConversionApi:RequestTimeoutSeconds` — timeout for a single HTTP request
- `ConversionApi:PollingIntervalSeconds` — polling interval for job status
- `ConversionApi:PollingTimeoutSeconds` — total polling timeout
- `ConversionApi:MaxRetries` — number of retries for network errors
- `Samples:*` — paths to demo files and the output directory

## Demo flow

1. The client reads `sample.fo` and calls the sync endpoint.
2. It saves the result in the output directory.
3. It reads `sample.xml` + `sample.xslt` and calls the async endpoint via JSON base64.
4. It polls the job status using `statusUrl` from the `202 Accepted` response until it reaches `completed`.
5. It downloads the final artifact using `downloadUrl` from the job status response and saves it in the output directory.

## Additional FO Samples

Additional XSL-FO documents were added to [FopClientExample/samples](../FopClientExample/samples) for manual testing:

- `sample-font-showcase.fo` — Latin and Base 14 fonts
- `sample-cyrillic.fo` — Russian, Ukrainian, and Bulgarian
- `sample-cjk.fo` — Chinese / Japanese / Korean
- `sample-rtl.fo` — Arabic / Hebrew, right-to-left blocks

The current demo client still uses `sample.fo`, but any of these files can be substituted in a manual `curl.exe` request or a custom test scenario.

In the compose environment, the backend image already includes `DejaVu`, `Liberation`, and `Noto` font packages, so Cyrillic and CJK samples render noticeably more stably than with Base 14 fallback. For local runs outside Docker, the result depends on which fonts the local JVM can see.