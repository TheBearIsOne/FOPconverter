using FopClientExample.Api;
using FopClientExample.Configuration;
using FopClientExample.Services;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Options;
using Serilog;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.Configure<ConversionApiOptions>(builder.Configuration.GetSection(ConversionApiOptions.SectionName));
builder.Services.Configure<SampleOptions>(builder.Configuration.GetSection(SampleOptions.SectionName));

builder.Services.AddHttpClient("conversion-api", (serviceProvider, client) =>
{
    var options = serviceProvider.GetRequiredService<IOptions<ConversionApiOptions>>().Value;
    client.BaseAddress = new Uri(options.BaseUrl);
    client.Timeout = TimeSpan.FromSeconds(options.RequestTimeoutSeconds);
});

Log.Logger = new LoggerConfiguration()
    .ReadFrom.Configuration(builder.Configuration)
    .WriteTo.Console()
    .CreateLogger();

builder.Services.AddSerilog();
builder.Services.AddSingleton<IConversionApiClient, ConversionApiClient>();
builder.Services.AddSingleton<DemoOrchestrator>();

using var host = builder.Build();
using var scope = host.Services.CreateScope();
var orchestrator = scope.ServiceProvider.GetRequiredService<DemoOrchestrator>();
await orchestrator.RunAsync(CancellationToken.None);