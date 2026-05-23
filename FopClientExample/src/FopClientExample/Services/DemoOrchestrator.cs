using FopClientExample.Api;
using FopClientExample.Configuration;
using FopClientExample.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace FopClientExample.Services;

public sealed class DemoOrchestrator
{
    private readonly IConversionApiClient _client;
    private readonly ConversionApiOptions _apiOptions;
    private readonly SampleOptions _sampleOptions;
    private readonly ILogger<DemoOrchestrator> _logger;

    public DemoOrchestrator(
        IConversionApiClient client,
        IOptions<ConversionApiOptions> apiOptions,
        IOptions<SampleOptions> sampleOptions,
        ILogger<DemoOrchestrator> logger)
    {
        _client = client;
        _apiOptions = apiOptions.Value;
        _sampleOptions = sampleOptions.Value;
        _logger = logger;
    }

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(_sampleOptions.OutputDirectory);

        var syncRequest = await BuildFoRequestAsync(cancellationToken);
        var syncStarted = DateTimeOffset.UtcNow;
        var syncResult = await _client.ConvertSyncAsync(syncRequest, cancellationToken);
        var syncPath = Path.Combine(_sampleOptions.OutputDirectory, syncResult.FileName);
        await File.WriteAllBytesAsync(syncPath, syncResult.Bytes, cancellationToken);
        _logger.LogInformation("Sync conversion completed in {ElapsedMs} ms. Saved to {Path}", (DateTimeOffset.UtcNow - syncStarted).TotalMilliseconds, syncPath);

        var asyncRequest = await BuildXmlXsltRequestAsync(cancellationToken);
        var accepted = await _client.ConvertAsyncAsync(asyncRequest, cancellationToken);
        _logger.LogInformation(
            "Async conversion accepted. JobId={JobId}, StatusUrl={StatusUrl}",
            accepted.JobId,
            accepted.StatusUrl ?? $"/api/v1/jobs/{accepted.JobId}");

        var jobStatus = await PollUntilCompletedAsync(accepted, cancellationToken);
        var download = await _client.DownloadAsync(jobStatus, cancellationToken);
        var asyncPath = Path.Combine(_sampleOptions.OutputDirectory, download.FileName);
        await File.WriteAllBytesAsync(asyncPath, download.Bytes, cancellationToken);
        _logger.LogInformation("Async conversion completed. Saved to {Path}", asyncPath);
    }

    private async Task<JobStatusResponse> PollUntilCompletedAsync(AcceptedJobResponse acceptedJob, CancellationToken cancellationToken)
    {
        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutCts.CancelAfter(TimeSpan.FromSeconds(_apiOptions.PollingTimeoutSeconds));

        while (!timeoutCts.Token.IsCancellationRequested)
        {
            var status = await _client.GetJobStatusAsync(acceptedJob, timeoutCts.Token);
            _logger.LogInformation(
                "Job {JobId} status: {Status}, DownloadUrl={DownloadUrl}",
                acceptedJob.JobId,
                status.Status,
                status.DownloadUrl ?? "<pending>");
            if (string.Equals(status.Status, "completed", StringComparison.OrdinalIgnoreCase))
            {
                return status;
            }

            if (string.Equals(status.Status, "failed", StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException($"Job failed with {status.ErrorCode}: {status.ErrorDetails}");
            }

            await Task.Delay(TimeSpan.FromSeconds(_apiOptions.PollingIntervalSeconds), timeoutCts.Token);
        }

        throw new TimeoutException($"Polling timed out for job {acceptedJob.JobId}.");
    }

    private async Task<ConversionUploadRequest> BuildFoRequestAsync(CancellationToken cancellationToken)
    {
        var path = ResolvePath(_sampleOptions.FoPath);
        return new ConversionUploadRequest(
            "XSL_FO",
            _apiOptions.OutputFormats[0],
            Path.GetFileName(path),
            await File.ReadAllBytesAsync(path, cancellationToken),
            null,
            null,
            Guid.NewGuid().ToString("N"));
    }

    private async Task<ConversionUploadRequest> BuildXmlXsltRequestAsync(CancellationToken cancellationToken)
    {
        var xmlPath = ResolvePath(_sampleOptions.XmlPath);
        var xsltPath = ResolvePath(_sampleOptions.XsltPath);
        return new ConversionUploadRequest(
            "XML_XSLT",
            _apiOptions.OutputFormats.ElementAtOrDefault(1) ?? "PDF",
            Path.GetFileName(xmlPath),
            await File.ReadAllBytesAsync(xmlPath, cancellationToken),
            Path.GetFileName(xsltPath),
            await File.ReadAllBytesAsync(xsltPath, cancellationToken),
            Guid.NewGuid().ToString("N"));
    }

    private static string ResolvePath(string configuredPath)
    {
        var candidates = new List<string>
        {
            Path.GetFullPath(configuredPath, AppContext.BaseDirectory),
            Path.GetFullPath(configuredPath, Directory.GetCurrentDirectory())
        };

        var current = new DirectoryInfo(AppContext.BaseDirectory);
        while (current is not null)
        {
            candidates.Add(Path.GetFullPath(configuredPath, current.FullName));
            current = current.Parent;
        }

        var resolved = candidates.FirstOrDefault(File.Exists);
        if (resolved is null)
        {
            throw new FileNotFoundException($"Sample file not found. Tried: {string.Join(", ", candidates.Distinct())}");
        }

        return resolved;
    }
}