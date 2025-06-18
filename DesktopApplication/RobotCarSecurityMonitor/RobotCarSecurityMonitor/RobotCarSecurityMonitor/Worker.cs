using System.Text.Json;
using System.Collections.Concurrent;

namespace RobotCarSecurityMonitor
{
    public class Worker : BackgroundService
    {
        private readonly ILogger<Worker> _logger;
        private readonly HttpClient _httpClient = new();

        private readonly string basePath = Path.Combine(
            Directory.GetParent(Directory.GetParent(AppContext.BaseDirectory)!.FullName)!.FullName,
            "Assets",
            "Videos");


        // tracking active device workers
        private readonly ConcurrentDictionary<string, DeviceWorker> _deviceWorkers = new();

        public Worker(ILogger<Worker> logger)
        {
            _logger = logger;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    var request = new HttpRequestMessage(HttpMethod.Get, "/devices");
                    request.Headers.Add("X-API-KEY", "");

                    var response = await _httpClient.SendAsync(request, stoppingToken);
                    response.EnsureSuccessStatusCode();

                    var json = await response.Content.ReadAsStringAsync(stoppingToken);
                    var deviceIds = JsonSerializer.Deserialize<string[]>(json) ?? Array.Empty<string>();

                    if (!Directory.Exists(basePath))
                        Directory.CreateDirectory(basePath);

                    // Create folders and start workers if not running yet
                    foreach (var deviceId in deviceIds)
                    {
                        string deviceFolder = Path.Combine(basePath, deviceId);
                        if (!Directory.Exists(deviceFolder))
                        {
                            Directory.CreateDirectory(deviceFolder);
                            _logger.LogInformation($"Created folder for device: {deviceId}");
                        }

                        // Start a DeviceWorker if not already running for this deviceId
                        if (!_deviceWorkers.ContainsKey(deviceId))
                        {
                            var deviceWorker = new DeviceWorker(deviceId, _logger, basePath);
                            if (_deviceWorkers.TryAdd(deviceId, deviceWorker))
                            {
                                _ = deviceWorker.StartAsync();
                                _logger.LogInformation($"Started device worker for {deviceId}");
                            }
                        }
                    }

                    // stop workers
                    var toRemove = _deviceWorkers.Keys.Except(deviceIds).ToList();
                    foreach (var missingDeviceId in toRemove)
                    {
                        if (_deviceWorkers.TryRemove(missingDeviceId, out var worker))
                        {
                            worker.Stop();
                            _logger.LogInformation($"Stopped device worker for {missingDeviceId}");
                        }
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Failed to fetch or process devices.");
                }

                await Task.Delay(TimeSpan.FromSeconds(30), stoppingToken);
            }

            // stop all workers
            foreach (var worker in _deviceWorkers.Values)
                worker.Stop();
        }
    }
}
