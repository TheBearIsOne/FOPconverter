namespace FopClientExample.Models;

public sealed record JobStatusResponse(Guid JobId, string Status, string? DownloadUrl, string? ErrorCode, string? ErrorDetails);