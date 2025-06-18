using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;

using RobotCarSecurity;

namespace RobotCarSecurity
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
            MainContent.Content = new HomeView(); // default view
            SetActiveNavButton(HomeButton);
        }

        private void SetActiveNavButton(Button activeButton)
        {
            foreach (var child in NavBarPanel.Children)
            {
                if (child is Button btn)
                {
                    btn.Tag = null;
                }
            }

            activeButton.Tag = "Active";
        }

        private void Home_Click(object sender, RoutedEventArgs e)
        {
            SetActiveNavButton((Button)sender);
            MainContent.Content = new HomeView();
        }

        private void Devices_Click(object sender, RoutedEventArgs e)
        {
            SetActiveNavButton((Button)sender);
            MainContent.Content = new DevicesView();
        }

        private void Footage_Click(object sender, RoutedEventArgs e)
        {
            SetActiveNavButton((Button)sender);
            MainContent.Content = new FootageView();
        }

        private void Detections_Click(object sender, RoutedEventArgs e)
        {
            SetActiveNavButton((Button)sender);
            MainContent.Content = new DetectionsView();
        }
    }
}
