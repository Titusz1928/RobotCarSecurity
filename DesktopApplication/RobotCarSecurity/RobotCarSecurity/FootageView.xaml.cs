using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;
using System.ServiceProcess;
using System.Threading.Tasks;
using System.Runtime.Versioning;
using System.Windows.Media;

namespace RobotCarSecurity
{
    [SupportedOSPlatform("windows")]
    public partial class FootageView : UserControl
    {
        private bool isDraggingSlider = false;
        private DispatcherTimer timer;
        private bool isPlaying = false;
        private const string ServiceName = "RobotCarSecurityMonitor";

        public ObservableCollection<DeviceFolder> Devices { get; set; }

        public FootageView()
        {
            InitializeComponent();

            Devices = new ObservableCollection<DeviceFolder>();
            LoadDevicesFromAssets();
            DevicesTreeView.ItemsSource = Devices;
            DevicesTreeView.AddHandler(TreeViewItem.PreviewMouseLeftButtonDownEvent, new MouseButtonEventHandler(TreeViewItem_PreviewMouseLeftButtonDown), true);

            // Timer to update the slider while video plays
            timer = new DispatcherTimer
            {
                Interval = TimeSpan.FromMilliseconds(500)
            };
            timer.Tick += Timer_Tick;

            Loaded += FootageView_Loaded;

            ShowDefaultPreview();
        }

        private void ShowDefaultPreview()
        {
            VideoPlayer.Stop();
            VideoPlayer.Source = null;
            DefaultImage.Visibility = Visibility.Visible;
            isPlaying = false;
            PlayPauseIcon.Text = "▶";
            timer.Stop();
        }

        private void TreeViewItem_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            if (e.OriginalSource is TextBlock textBlock)
            {
                var treeViewItem = FindAncestor<TreeViewItem>(textBlock);
                if (treeViewItem != null)
                {
                    treeViewItem.IsExpanded = !treeViewItem.IsExpanded;
                    treeViewItem.IsSelected = true;
                    e.Handled = true;
                }
            }
        }

        private static T FindAncestor<T>(DependencyObject current) where T : DependencyObject
        {
            while (current != null && !(current is T))
            {
                current = VisualTreeHelper.GetParent(current);
            }
            return current as T;
        }

        private void LoadDevicesFromAssets()
        {
            // Get path to the folder containing the executable
            string exeFolder = AppDomain.CurrentDomain.BaseDirectory;

            string rootFolder = Path.GetFullPath(Path.Combine(exeFolder, ".."));

            string basePath = Path.Combine(rootFolder, "Assets", "Videos");

            //MessageBox.Show($"Looking for videos at: {basePath}");

            if (!Directory.Exists(basePath)) return;

            var deviceFolders = Directory.GetDirectories(basePath);

            foreach (var deviceFolderPath in deviceFolders)
            {
                var deviceFolder = new DeviceFolder
                {
                    DeviceId = Path.GetFileName(deviceFolderPath),
                    Videos = new ObservableCollection<VideoFile>()
                };

                var videoFiles = Directory.GetFiles(deviceFolderPath, "*.mp4");

                foreach (var videoFilePath in videoFiles)
                {
                    deviceFolder.Videos.Add(new VideoFile
                    {
                        FileName = Path.GetFileName(videoFilePath),
                        FullPath = videoFilePath
                    });
                }

                Devices.Add(deviceFolder);
            }
        }


        private void DevicesTreeView_SelectedItemChanged(object sender, RoutedPropertyChangedEventArgs<object> e)
        {
            if (DevicesTreeView.SelectedItem is VideoFile selectedVideo)
            {
                try
                {
                    DefaultImage.Visibility = Visibility.Collapsed;
                    VideoPlayer.Stop();
                    VideoPlayer.Source = new Uri(selectedVideo.FullPath, UriKind.Absolute);
                    VideoPlayer.SpeedRatio = (float)SpeedSlider.Value;
                    VideoPlayer.Play();
                    isPlaying = true;
                    PlayPauseIcon.Text = "⏸";

                    // Start timer to update slider
                    timer.Start();
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Error playing video: {ex.Message}");
                }
            }
        }

        private string FormatTime(TimeSpan time)
        {
            return time.ToString(@"mm\:ss");
        }

        private void Timer_Tick(object sender, EventArgs e)
        {
            if ((VideoPlayer.NaturalDuration.HasTimeSpan) && (!isDraggingSlider))
            {
                TimeSpan totalDuration = VideoPlayer.NaturalDuration.TimeSpan;
                TimeSpan currentPosition = VideoPlayer.Position;

                SeekSlider.Minimum = 0;
                SeekSlider.Maximum = totalDuration.TotalSeconds;
                SeekSlider.Value = currentPosition.TotalSeconds;

                CurrentTimeText.Text = FormatTime(currentPosition);
                TotalDurationText.Text = FormatTime(totalDuration);
            }
        }

        // When user starts dragging the slider
        private void SeekSlider_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            isDraggingSlider = true;
        }

        // When user releases the slider, seek video
        private void SeekSlider_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            VideoPlayer.Position = TimeSpan.FromSeconds(SeekSlider.Value);
            isDraggingSlider = false;
        }

        // Change playback speed
        private void SpeedSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (VideoPlayer == null) return;

            VideoPlayer.SpeedRatio = SpeedSlider.Value;

           
            if (SpeedLabel != null)
            {
                SpeedLabel.Text = $"{SpeedSlider.Value:0.00}x";
            }
        }

        private void PlayPauseButton_Click(object sender, RoutedEventArgs e)
        {
            if (!isPlaying)
            {
                VideoPlayer.Play();
                isPlaying = true;
                PlayPauseIcon.Text = "⏸";
                timer.Start();
            }
            else
            {
                VideoPlayer.Pause();
                isPlaying = false;
                PlayPauseIcon.Text = "▶";
                timer.Stop();
            }
        }

        [SupportedOSPlatform("windows")]
        private async void ServiceToggleCheckBox_Checked(object sender, RoutedEventArgs e)
        {
            ServiceToggleCheckBox.IsEnabled = false;
            await StartServiceAsync();
            ServiceToggleCheckBox.IsEnabled = true;
        }

        [SupportedOSPlatform("windows")]
        private async void ServiceToggleCheckBox_Unchecked(object sender, RoutedEventArgs e)
        {
            ServiceToggleCheckBox.IsEnabled = false;
            await StopServiceAsync();
            ServiceToggleCheckBox.IsEnabled = true;
        }

        [SupportedOSPlatform("windows")]
        private async Task StartServiceAsync()
        {
            try
            {
                using (var sc = new ServiceController(ServiceName))
                {
                    if (sc.Status != ServiceControllerStatus.Running && sc.Status != ServiceControllerStatus.StartPending)
                    {
                        sc.Start();
                        await Task.Run(() => sc.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(10)));
                    }
                }
            }
            catch (InvalidOperationException ex)
            {
                MessageBox.Show($"InvalidOperationException:\n{ex.Message}\n\n{ex.StackTrace}");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Error: {ex.Message}\n\n{ex.GetType()}\n{ex.StackTrace}");
                ServiceToggleCheckBox.IsChecked = false;
            }
        }

        [SupportedOSPlatform("windows")]
        private async Task StopServiceAsync()
        {
            try
            {
                //MessageBox.Show($"Service name: '{ServiceName}'");
                using (var sc = new ServiceController(ServiceName))
                {
                    if (sc.Status != ServiceControllerStatus.Stopped && sc.Status != ServiceControllerStatus.StopPending)
                    {
                        sc.Stop();
                        await Task.Run(() => sc.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(10)));
                    }
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Failed to stop service: {ex.Message}");
                ServiceToggleCheckBox.IsChecked = true;
            }
        }

        [SupportedOSPlatform("windows")]
        private bool IsServiceRunning()
        {
            try
            {
                using var sc = new ServiceController(ServiceName);
                return sc.Status == ServiceControllerStatus.Running;
            }
            catch
            {
                return false;
            }
        }

        private void FootageView_Loaded(object sender, RoutedEventArgs e)
        {
            ServiceToggleCheckBox.Checked -= ServiceToggleCheckBox_Checked;
            ServiceToggleCheckBox.Unchecked -= ServiceToggleCheckBox_Unchecked;

            ServiceToggleCheckBox.IsChecked = IsServiceRunning();

            ServiceToggleCheckBox.Checked += ServiceToggleCheckBox_Checked;
            ServiceToggleCheckBox.Unchecked += ServiceToggleCheckBox_Unchecked;
        }


    }

    public class DeviceFolder
    {
        public string DeviceId { get; set; }
        public ObservableCollection<VideoFile> Videos { get; set; } = new ObservableCollection<VideoFile>();
    }

    public class VideoFile
    {
        public string FileName { get; set; }
        public string FullPath { get; set; }
    }


}
