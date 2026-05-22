namespace FopClientExample.Configuration;

public sealed class ConversionApiOptions
{
    public const string SectionName = "ConversionApi";

    public string BaseUrl { get; set; } = "http://localhost:8080";
    public int RequestTimeoutSeconds { get; set; } = 60;
    public int PollingIntervalSeconds { get; set; } = 2;
    public int PollingTimeoutSeconds { get; set; } = 120;
    public int MaxRetries { get; set; } = 3;
    public string[] OutputFormats { get; set; } = ["PDF", "TXT"];
}