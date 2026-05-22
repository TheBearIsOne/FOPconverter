namespace FopClientExample.Models;

public sealed record DownloadedConversionFile(string FileName, byte[] Bytes, string ContentType);