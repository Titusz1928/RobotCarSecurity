using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace RobotCarSecurityMonitor
{
    public class DeviceWorker
    {
        private readonly string _deviceId;
        private readonly ILogger _logger;
        private readonly HttpClient _httpClient = new();
        private CancellationTokenSource _cts = new();
        private Task? _workerTask;

        private readonly string _deviceFolder;
        private readonly int _frameIntervalSeconds = 5;
        private readonly int _framesPerVideo = 10; // 10 frames * 5 sec = 50 second video

        private readonly List<string> _frameFiles = new();

        private int _consecutiveFailures = 0;
        private const int MaxFailures = 3;
        private int _frameCounter = 0;

        public DeviceWorker(string deviceId, ILogger logger, string videosBasePath)
        {
            _deviceId = deviceId;
            _logger = logger;

            _deviceFolder = Path.Combine(videosBasePath, deviceId);
            Directory.CreateDirectory(_deviceFolder);
        }

        public Task StartAsync()
        {
            _cts = new CancellationTokenSource();
            _workerTask = Task.Run(() => DoWorkAsync(_cts.Token));
            return _workerTask;
        }

        public void Stop()
        {
            _cts.Cancel();
        }

        private async Task DoWorkAsync(CancellationToken token)
        {
            _logger.LogInformation($"DeviceWorker started for {_deviceId}");
            while (!token.IsCancellationRequested)
            {
                try
                {
                    var frame = await FetchFrameAsync();
                    _logger.LogInformation($"Fetched frame for {_deviceId}");

                    if (frame == null)
                    {
                        _consecutiveFailures++;
                        _logger.LogWarning($"Failed to fetch frame for {_deviceId}. Failures: {_consecutiveFailures}");

                        if (_consecutiveFailures >= MaxFailures)
                        {
                            _logger.LogError($"Max failures reached for {_deviceId}. Continuing...");
                            _consecutiveFailures = 0;
                        }
                    }
                    else
                    {
                        _logger.LogInformation($"Frame count for {_deviceId}: {_frameFiles.Count}/{_framesPerVideo}");
                        _consecutiveFailures = 0;
                        string frameFile = SaveFrameToFile(frame);
                        _frameFiles.Add(frameFile);

                        _logger.LogDebug($"Checking condition: {_frameFiles.Count} >= {_framesPerVideo}");

                        if (_frameCounter >= _framesPerVideo)
                        {
                            string videoFile = Path.Combine(_deviceFolder, $"video_{DateTime.Now:yyyyMMdd_HHmmss}.mp4");
                            CreateVideoFromFrames(_frameFiles, videoFile);
                            _logger.LogDebug($"Clearing frame list. Count before clear: {_frameFiles.Count}");
                            CleanupFrames(_frameFiles);
                            _frameFiles.Clear();
                            _frameCounter = 0; // Reset the frame counter
                            _logger.LogInformation($"Created video {videoFile} for {_deviceId}");
                        }
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, $"Error in DeviceWorker for {_deviceId}");
                }

                try
                {
                    await Task.Delay(TimeSpan.FromSeconds(_frameIntervalSeconds), token);
                }
                catch (TaskCanceledException)
                {
                    _logger.LogInformation($"DeviceWorker delay canceled for {_deviceId}");
                    return;
                }
            }
            _logger.LogInformation($"DeviceWorker stopped for {_deviceId}");
        }

        private async Task<byte[]?> FetchFrameAsync()
        {
            var url = $"/current_frame?device_id={_deviceId}";
            var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.Add("X-API-KEY", "");

            var response = await _httpClient.SendAsync(request);
            if (!response.IsSuccessStatusCode)
                return null;

            return await response.Content.ReadAsByteArrayAsync();
        }

        private string SaveFrameToFile(byte[] frame)
        {
            string fileName = Path.Combine(_deviceFolder, $"frame_{_frameCounter:D6}.jpg");
            File.WriteAllBytes(fileName, frame);
            _frameCounter++;
            _logger.LogInformation($"Saved frame to {fileName}");
            return fileName;
        }

        private void CleanupFrames(List<string> frameFiles)
        {
            foreach (var file in frameFiles)
            {
                try
                {
                    if (File.Exists(file))
                    {
                        File.Delete(file);
                        _logger.LogInformation($"Deleted frame: {file}");
                    }
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, $"Failed to delete frame: {file}");
                }
            }
        }

        private bool AddTimestampToFrame(string inputPath, string outputPath, DateTime timestamp)
        {
            var formattedTimestamp = timestamp.ToString("yyyy-MM-dd HH:mm:ss")
    .Replace(":", "\\:");

            var startInfo = new ProcessStartInfo
            {
                FileName = "ffmpeg",
                Arguments = $"-hide_banner -loglevel error -i \"{inputPath}\" -vf " +
    $"\"drawtext=fontfile='/Windows/Fonts/arial.ttf':text='{formattedTimestamp}':" +
    "fontcolor=white:fontsize=24:x=10:y=h-th-10:box=1:boxcolor=black@0.5:boxborderw=5\" " +
    $"-y \"{outputPath}\"",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = new Process { StartInfo = startInfo };

            var stderr = new StringBuilder();
            var stdout = new StringBuilder();

            process.OutputDataReceived += (sender, e) => { if (e.Data != null) stdout.AppendLine(e.Data); };
            process.ErrorDataReceived += (sender, e) => { if (e.Data != null) stderr.AppendLine(e.Data); };

            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            process.WaitForExit();

            if (process.ExitCode != 0)
            {
                _logger.LogError($"Failed to add timestamp to frame: {stderr}");
                return false;
            }

            return true;
        }


        private void CreateVideoFromFrames(List<string> frames, string outputVideo)
        {
            // sort frames
            var sortedFrames = frames.OrderBy(f => f).ToList();

            // Create a temporary directory
            string tempDir = Path.Combine(_deviceFolder, "temp_frames");
            Directory.CreateDirectory(tempDir);

            try
            {
                // Copy frames to temporary directory
                for (int i = 0; i < sortedFrames.Count; i++)
                {
                    var original = sortedFrames[i];
                    var creationTime = File.GetLastWriteTime(original);

                    string newPath = Path.Combine(tempDir, $"frame_{i:D6}.jpg");

                    if (!AddTimestampToFrame(original, newPath, creationTime))
                    {
                        _logger.LogError($"Skipping frame {original} due to timestamp failure.");
                        continue;
                    }
                }

                string framePattern = Path.Combine(tempDir, "frame_%06d.jpg");

                var startInfo = new ProcessStartInfo
                {
                    FileName = "ffmpeg",
                    Arguments = $"-framerate 1/{_frameIntervalSeconds} -i \"{framePattern}\" -c:v libx264 -r 30 -pix_fmt yuv420p \"{outputVideo}\" -y",
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                };



                using var process = new Process();
                process.StartInfo = startInfo;

                var stdOut = new StringBuilder();
                var stdErr = new StringBuilder();

                process.OutputDataReceived += (sender, args) => { if (args.Data != null) stdOut.AppendLine(args.Data); };
                process.ErrorDataReceived += (sender, args) => { if (args.Data != null) stdErr.AppendLine(args.Data); };

                process.Start();

                process.BeginOutputReadLine();
                process.BeginErrorReadLine();

                if (!process.WaitForExit(15000))
                {
                    process.Kill();
                    _logger.LogError("ffmpeg process timed out and was killed.");
                }
                else if (process.ExitCode != 0)
                {
                    _logger.LogError($"FFmpeg failed with exit code {process.ExitCode}. Error: {stdErr}");
                }
                else
                {
                    _logger.LogInformation($"Successfully created video: {outputVideo}");
                }

                _logger.LogDebug($"FFmpeg output: {stdOut}");
                _logger.LogDebug($"FFmpeg errors: {stdErr}");
            }
            finally
            {
                // Clean up temporary directory
                try
                {
                    Directory.Delete(tempDir, true);
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, $"Failed to delete temporary frames directory: {tempDir}");
                }
            }
        }
    }
}