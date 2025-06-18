using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

using RobotCarSecurity.Models;

namespace RobotCarSecurity
{
    public partial class DetectionsView : UserControl
    {
        private const string ApiKey = "";
        private const string ApiUrl = "/api/detections";

        private List<ImageInfo> images;

        public DetectionsView()
        {
            InitializeComponent();
            LoadDetections();
        }

        private async void LoadDetections()
        {
            try
            {
                using (HttpClient client = new HttpClient())
                {
                    client.DefaultRequestHeaders.Add("X-API-KEY", ApiKey);
                    HttpResponseMessage response = await client.GetAsync(ApiUrl);
                    response.EnsureSuccessStatusCode();

                    string json = await response.Content.ReadAsStringAsync();
                    images = JsonSerializer.Deserialize<List<ImageInfo>>(json, new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    });

                    foreach (var image in images)
                    {
                        if (DateTime.TryParse(image.Created, out DateTime dt))
                        {
                            image.Created = dt.ToString("yyyy-MM-dd HH:mm:ss");
                        }
                    }

                    DetectionsGrid.ItemsSource = images;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Failed to load detections: {ex.Message}");
            }
        }

        private void DetectionsGrid_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (DetectionsGrid.SelectedItem is ImageInfo selected)
            {
                try
                {
                    var bitmap = new BitmapImage();
                    bitmap.BeginInit();
                    bitmap.UriSource = new Uri(selected.Url);
                    bitmap.CacheOption = BitmapCacheOption.OnLoad;
                    bitmap.EndInit();

                    SelectedImage.Source = bitmap;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Failed to load image: {ex.Message}");
                    SelectedImage.Source = null;
                }
            }
            else
            {
                SelectedImage.Source = null;
            }
        }
    }
}
