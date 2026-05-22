namespace FopClientExample.Models;

public sealed record ConversionUploadRequest(
    string PayloadType,
    string OutputFormat,
    string PrimaryFileName,
    byte[] PrimaryBytes,
    string? SecondaryFileName,
    byte[]? SecondaryBytes,
    string CorrelationId);