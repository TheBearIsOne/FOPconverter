using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using FopClientExample.Configuration;
using FopClientExample.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace FopClientExample.Api;

public sealed class ConversionApiClient : IConversionApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ConversionApiOptions _options;
    private readonly ILogger<ConversionApiClient> _logger;

    public ConversionApiClient(
        IHttpClientFactory httpClientFactory,
        IOptions<ConversionApiOptions> options,
        ILogger<ConversionApiClient> logger)
    {
        _httpClientFactory = httpClientFactory;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<DownloadedConversionFile> ConvertSyncAsync(ConversionUploadRequest request, CancellationToken cancellationToken)
    {
        using var httpRequest = new HttpRequestMessage(HttpMethod.Post, "/api/v1/convert/sync")
        {
            Content = BuildMultipartContent(request)
        };

        using var response = await SendWithRetryAsync(
            () => CloneRequestAsync(httpRequest),
            cancellationToken);

        response.EnsureSuccessStatusCode();
        return await ReadDownloadAsync(response, request.OutputFormat, cancellationToken);
    }

    public async Task<AcceptedJobResponse> ConvertAsyncAsync(ConversionUploadRequest request, CancellationToken cancellationToken)
    {
        var payload = new ConvertJsonRequest(
            request.PayloadType,
            request.OutputFormat,
            Convert.ToBase64String(request.PrimaryBytes),
            request.PrimaryFileName,
            request.SecondaryBytes is null ? null : Convert.ToBase64String(request.SecondaryBytes),
            request.SecondaryFileName,
            request.CorrelationId);

        using var httpRequest = new HttpRequestMessage(HttpMethod.Post, "/api/v1/convert")
        {
            Content = JsonContent.Create(payload)
        };

        using var response = await SendWithRetryAsync(
            () => CloneRequestAsync(httpRequest),
            cancellationToken);

        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<AcceptedJobResponse>(JsonOptions, cancellationToken)
               ?? throw new InvalidOperationException("Async conversion response was empty.");
    }

    public async Task<JobStatusResponse> GetJobStatusAsync(Guid jobId, CancellationToken cancellationToken)
    {
        return await GetJobStatusAsync(CreateRequestUri($"/api/v1/jobs/{jobId}"), cancellationToken);
    }

    public async Task<JobStatusResponse> GetJobStatusAsync(AcceptedJobResponse acceptedJob, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(acceptedJob);
        return await GetJobStatusAsync(ResolveStatusUri(acceptedJob), cancellationToken);
    }

    public async Task<DownloadedConversionFile> DownloadAsync(Guid jobId, CancellationToken cancellationToken)
    {
        return await DownloadAsync(CreateRequestUri($"/api/v1/jobs/{jobId}/download"), cancellationToken);
    }

    public async Task<DownloadedConversionFile> DownloadAsync(JobStatusResponse jobStatus, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(jobStatus);
        return await DownloadAsync(ResolveDownloadUri(jobStatus), cancellationToken);
    }

    private async Task<JobStatusResponse> GetJobStatusAsync(Uri requestUri, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, requestUri);
        using var response = await SendWithRetryAsync(() => CloneRequestAsync(request), cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<JobStatusResponse>(JsonOptions, cancellationToken)
               ?? throw new InvalidOperationException("Job status response was empty.");
    }

    private async Task<DownloadedConversionFile> DownloadAsync(Uri requestUri, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, requestUri);
        using var response = await SendWithRetryAsync(() => CloneRequestAsync(request), cancellationToken);
        response.EnsureSuccessStatusCode();
        return await ReadDownloadAsync(response, null, cancellationToken);
    }

    private static Uri ResolveStatusUri(AcceptedJobResponse acceptedJob)
    {
        return CreateRequestUri(string.IsNullOrWhiteSpace(acceptedJob.StatusUrl)
            ? $"/api/v1/jobs/{acceptedJob.JobId}"
            : acceptedJob.StatusUrl);
    }

    private static Uri ResolveDownloadUri(JobStatusResponse jobStatus)
    {
        return CreateRequestUri(string.IsNullOrWhiteSpace(jobStatus.DownloadUrl)
            ? $"/api/v1/jobs/{jobStatus.JobId}/download"
            : jobStatus.DownloadUrl);
    }

    private static Uri CreateRequestUri(string relativeOrAbsoluteUri)
    {
        return Uri.TryCreate(relativeOrAbsoluteUri, UriKind.Absolute, out var absoluteUri)
            ? absoluteUri
            : new Uri(relativeOrAbsoluteUri, UriKind.Relative);
    }

    private async Task<HttpResponseMessage> SendWithRetryAsync(
        Func<Task<HttpRequestMessage>> requestFactory,
        CancellationToken cancellationToken)
    {
        var client = _httpClientFactory.CreateClient("conversion-api");
        Exception? lastError = null;

        for (var attempt = 1; attempt <= _options.MaxRetries; attempt++)
        {
            try
            {
                using var request = await requestFactory();
                var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
                if (response.StatusCode is HttpStatusCode.TooManyRequests or >= HttpStatusCode.InternalServerError)
                {
                    lastError = new HttpRequestException($"Transient status code {(int)response.StatusCode}");
                    response.Dispose();
                }
                else
                {
                    return response;
                }
            }
            catch (HttpRequestException exception)
            {
                lastError = exception;
            }

            var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
            _logger.LogWarning(lastError, "HTTP call failed on attempt {Attempt}. Retrying in {Delay}.", attempt, delay);
            await Task.Delay(delay, cancellationToken);
        }

        throw new HttpRequestException("HTTP call failed after retries.", lastError);
    }

    private static MultipartFormDataContent BuildMultipartContent(ConversionUploadRequest request)
    {
        var content = new MultipartFormDataContent();
        content.Add(new StringContent(request.PayloadType), "payloadType");
        content.Add(new StringContent(request.OutputFormat), "outputFormat");
        content.Add(new ByteArrayContent(request.PrimaryBytes), "primaryDocument", request.PrimaryFileName);

        if (request.SecondaryBytes is not null && request.SecondaryFileName is not null)
        {
            content.Add(new ByteArrayContent(request.SecondaryBytes), "secondaryDocument", request.SecondaryFileName);
        }

        if (!string.IsNullOrWhiteSpace(request.CorrelationId))
        {
            content.Add(new StringContent(request.CorrelationId), "correlationId");
        }

        return content;
    }

    private static async Task<HttpRequestMessage> CloneRequestAsync(HttpRequestMessage request)
    {
        var clone = new HttpRequestMessage(request.Method, request.RequestUri);
        foreach (var header in request.Headers)
        {
            clone.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        if (request.Content is not null)
        {
            var memoryStream = new MemoryStream();
            await request.Content.CopyToAsync(memoryStream);
            memoryStream.Position = 0;
            var streamContent = new StreamContent(memoryStream);
            foreach (var header in request.Content.Headers)
            {
                streamContent.Headers.TryAddWithoutValidation(header.Key, header.Value);
            }

            clone.Content = streamContent;
        }

        return clone;
    }

    private static async Task<DownloadedConversionFile> ReadDownloadAsync(
        HttpResponseMessage response,
        string? fallbackFormat,
        CancellationToken cancellationToken)
    {
        var bytes = await response.Content.ReadAsByteArrayAsync(cancellationToken);
        var fileName = response.Content.Headers.ContentDisposition?.FileNameStar
                       ?? response.Content.Headers.ContentDisposition?.FileName
                       ?? $"conversion.{fallbackFormat?.ToLowerInvariant() ?? "bin"}";

        return new DownloadedConversionFile(fileName.Trim('"'), bytes, response.Content.Headers.ContentType?.MediaType ?? "application/octet-stream");
    }

    private sealed record ConvertJsonRequest(
        string PayloadType,
        string OutputFormat,
        string PrimaryDocumentBase64,
        string PrimaryFileName,
        string? SecondaryDocumentBase64,
        string? SecondaryFileName,
        string CorrelationId);
}