namespace FopClientExample.Models;

public sealed record AcceptedJobResponse(Guid JobId, string Status, string? StatusUrl);