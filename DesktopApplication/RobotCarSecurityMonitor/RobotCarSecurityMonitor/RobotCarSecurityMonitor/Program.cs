using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Hosting.WindowsServices;
using RobotCarSecurityMonitor;

var isService = !(Environment.UserInteractive || args.Contains("--console"));

var builder = Host.CreateDefaultBuilder(args)
    .ConfigureServices((hostContext, services) =>
    {
        services.AddHostedService<Worker>();
    })
    .ConfigureLogging((hostContext, logging) =>
    {
        logging.ClearProviders();

        if (isService)
        {
            logging.AddEventLog(settings =>
            {
                settings.SourceName = "RobotCarSecurityMonitor";
                settings.LogName = "Application";
            });
        }
        else
        {
            logging.AddConsole();
        }
    });

if (isService)
{
    builder.UseWindowsService();
}
else
{
    builder.UseConsoleLifetime();
}

var host = builder.Build();

if (isService)
{
    var logger = host.Services.GetRequiredService<ILogger<Program>>();
    logger.LogInformation("Service starting...");
}

host.Run();