using FopClientExample.Models;

namespace FopClientExample.Api;

public interface IConversionApiClient
{
    Task<DownloadedConversionFile> ConvertSyncAsync(ConversionUploadRequest request, CancellationToken cancellationToken);
    Task<AcceptedJobResponse> ConvertAsyncAsync(ConversionUploadRequest request, CancellationToken cancellationToken);
    Task<JobStatusResponse> GetJobStatusAsync(Guid jobId, CancellationToken cancellationToken);
    Task<JobStatusResponse> GetJobStatusAsync(AcceptedJobResponse acceptedJob, CancellationToken cancellationToken);
    Task<DownloadedConversionFile> DownloadAsync(Guid jobId, CancellationToken cancellationToken);
    Task<DownloadedConversionFile> DownloadAsync(JobStatusResponse jobStatus, CancellationToken cancellationToken);
}