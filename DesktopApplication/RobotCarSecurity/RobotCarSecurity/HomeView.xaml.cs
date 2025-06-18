using System;
using System.Collections.ObjectModel;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

using RobotCarSecurity.Models;

namespace RobotCarSecurity
{
    public partial class HomeView : UserControl
    {
        private ObservableCollection<BitmapImage> recentImages = new ObservableCollection<BitmapImage>();

        private readonly string apiDetectionsUrl = "/api/detections?count=3";
        private readonly string apiKey = "";

        public HomeView()
        {
            InitializeComponent();
            RecentCapturesList.ItemsSource = recentImages;
            _ = LoadRecentCapturesAsync();
        }

        private async Task LoadRecentCapturesAsync()
        {
            try
            {
                using HttpClient client = new HttpClient();

                client.DefaultRequestHeaders.Add("X-API-KEY", apiKey);

                // Send GET request
                var response = await client.GetAsync(apiDetectionsUrl);
                response.EnsureSuccessStatusCode();

                var json = await response.Content.ReadAsStringAsync();

                // Parse JSON response
                var images = JsonSerializer.Deserialize<ImageInfo[]>(json, new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                });

                recentImages.Clear();

                foreach (var img in images)
                {
                    var bitmap = new BitmapImage();
                    bitmap.BeginInit();
                    bitmap.UriSource = new Uri(img.Url);
                    bitmap.CacheOption = BitmapCacheOption.OnLoad;
                    bitmap.EndInit();

                    recentImages.Add(bitmap);
                }
            }
            catch (Exception ex)
            {
                System.Windows.MessageBox.Show($"Failed to load recent captures: {ex.Message}");
            }
        }
    }

}
