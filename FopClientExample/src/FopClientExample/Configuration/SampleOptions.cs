namespace FopClientExample.Configuration;

public sealed class SampleOptions
{
    public const string SectionName = "Samples";

    public string FoPath { get; set; } = "../../samples/sample.fo";
    public string XmlPath { get; set; } = "../../samples/sample.xml";
    public string XsltPath { get; set; } = "../../samples/sample.xslt";
    public string OutputDirectory { get; set; } = "../../output";
}