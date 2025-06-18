using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace RobotCarSecurity
{
    public partial class DevicesView : UserControl
    {
        private readonly HttpClient _httpClient = new HttpClient();
        private DispatcherTimer _timer;

        public DevicesView()
        {
            InitializeComponent();
            Loaded += DevicesView_Loaded;

            DevicesComboBox.SelectionChanged += DevicesComboBox_SelectionChanged;

            _timer = new DispatcherTimer
            {
                Interval = TimeSpan.FromSeconds(1)
            };
            _timer.Tick += Timer_Tick;
        }

        private async void DevicesView_Loaded(object sender, RoutedEventArgs e)
        {
            await LoadDevicesAsync();
        }

        private async Task LoadDevicesAsync()
        {
            try
            {
                string apiUrl = "/devices";
                _httpClient.DefaultRequestHeaders.Clear();
                _httpClient.DefaultRequestHeaders.Add("X-Api-Key", "");

                var response = await _httpClient.GetAsync(apiUrl);
                response.EnsureSuccessStatusCode();

                var json = await response.Content.ReadAsStringAsync();
                var devices = JsonSerializer.Deserialize<List<string>>(json);

                DevicesComboBox.ItemsSource = devices;
                //MessageBox.Show($"Devices added");
            }
            catch (HttpRequestException ex)
            {
                MessageBox.Show($"Error loading devices: {ex.Message}");
            }
            catch (JsonException ex)
            {
                MessageBox.Show($"Error parsing device list: {ex.Message}");
            }
        }

        private void DevicesComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (DevicesComboBox.SelectedItem != null)
            {
                string selectedDevice = DevicesComboBox.SelectedItem.ToString();
                OverlayDeviceName.Text = selectedDevice;
                _timer.Start();
            }
            else
            {
                OverlayDeviceName.Text = "No Device Selected";
                _timer.Stop();
                SetDefaultImage();
            }
        }

        private async void Timer_Tick(object sender, EventArgs e)
        {
            if (DevicesComboBox.SelectedItem == null)
            {
                _timer.Stop();
                SetDefaultImage();
                OverlayDistance.Text = "No distance data";
                return;
            }

            string selectedDevice = DevicesComboBox.SelectedItem.ToString();

            await UpdateDeviceImageAsync(selectedDevice);
            await UpdateDeviceDataAsync(selectedDevice);
        }

        private async Task UpdateDeviceImageAsync(string deviceId)
        {
            try
            {
                string imageUrl = $"/current_frame?device_id={Uri.EscapeDataString(deviceId)}";

                _httpClient.DefaultRequestHeaders.Clear();
                _httpClient.DefaultRequestHeaders.Add("X-Api-Key", "");

                var response = await _httpClient.GetAsync(imageUrl);
                if (!response.IsSuccessStatusCode)
                {
                    
                    SetDefaultImage();
                    return;
                }

                var imageBytes = await response.Content.ReadAsByteArrayAsync();

                using (var ms = new MemoryStream(imageBytes))
                {
                    var bitmap = new BitmapImage();
                    bitmap.BeginInit();
                    bitmap.CacheOption = BitmapCacheOption.OnLoad;
                    bitmap.StreamSource = ms;
                    bitmap.EndInit();
                    bitmap.Freeze();

                    DeviceImage.Source = bitmap;
                }
            }
            catch (Exception)
            {
                // fallback to default image
                SetDefaultImage();
            }
        }

        private async Task UpdateDeviceDataAsync(string deviceId)
        {
            try
            {
                string dataUrl = $"/current_data?device_id={Uri.EscapeDataString(deviceId)}";

                _httpClient.DefaultRequestHeaders.Clear();
                _httpClient.DefaultRequestHeaders.Add("X-Api-Key", "");

                var response = await _httpClient.GetAsync(dataUrl);
                response.EnsureSuccessStatusCode();

                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);

                if (doc.RootElement.TryGetProperty("data", out JsonElement dataElement))
                {
                    string rawData = dataElement.GetString();

                    if (rawData.StartsWith("distance:"))
                    {
                        string distanceValue = rawData.Substring("distance:".Length);
                        OverlayDistance.Text = $"Distance: {distanceValue}";
                    }
                    else
                    {
                        OverlayDistance.Text = "Distance format invalid";
                    }
                }
                else
                {
                    OverlayDistance.Text = "Distance data missing";
                }
            }
            catch (Exception ex)
            {
                OverlayDistance.Text = $"Error: {ex.Message}";
            }
        }

        private async void SendCommand_Click(object sender, RoutedEventArgs e)
        {
            if (DevicesComboBox.SelectedItem == null)
            {
                MessageBox.Show("Please select a device first.");
                return;
            }

            string deviceId = DevicesComboBox.SelectedItem.ToString();
            string command = (sender as Button)?.Tag?.ToString().ToUpper();

            if (string.IsNullOrEmpty(command))
                return;

            try
            {
                string apiUrl = "/send";
                _httpClient.DefaultRequestHeaders.Clear();
                _httpClient.DefaultRequestHeaders.Add("X-Api-Key", "");

                var content = new FormUrlEncodedContent(new[]
                {
            new KeyValuePair<string, string>("message", command),
            new KeyValuePair<string, string>("device_id", deviceId)
        });

                var response = await _httpClient.PostAsync(apiUrl, content);
                response.EnsureSuccessStatusCode();

               
                //MessageBox.Show($"Command '{command}' sent to {deviceId}.");
            }
            catch (HttpRequestException ex)
            {
                MessageBox.Show($"Failed to send command: {ex.Message}");
            }
        }



        private void SetDefaultImage()
        {
          
            DeviceImage.Source = new BitmapImage(new Uri("Assets/noconnection.png", UriKind.Relative));
        }
    }
}
