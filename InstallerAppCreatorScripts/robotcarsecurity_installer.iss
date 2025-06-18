[Setup]
AppName=RobotCar Security
AppVersion=1.01
DefaultDirName={pf}\RobotCarApp
OutputDir=.
OutputBaseFilename=RobotCarInstaller 
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
SetupIconFile=C:\Users\Boros Titusz\University\UPT\LICENTA\desktopApp\RobotCarSecurity\RobotCarSecurity\Assets\logo.ico

[Files]
; WPF UI
Source:"C:\Users\Boros Titusz\University\UPT\LICENTA\desktopApp\RobotCarSecurity\publish\*"; DestDir:"{app}\UI"; Flags: ignoreversion recursesubdirs createallsubdirs

; Worker Service
Source:"C:\Users\Boros Titusz\University\UPT\LICENTA\desktopApp\RobotCarSecurityMonitor\RobotCarSecurityMonitor\publish\*"; DestDir:"{app}\Service"; Flags: ignoreversion recursesubdirs createallsubdirs

; FFmpeg folder
Source:"C:\FFmpeg\*"; DestDir:"{app}\Service\ffmpeg"; Flags: ignoreversion recursesubdirs createallsubdirs

; Service install batch file
Source: "C:\Users\Boros Titusz\University\UPT\LICENTA\desktopApp\RobotCarSecurityMonitor\RobotCarSecurityMonitor\publish\install_setup.bat"; DestDir: "{app}\Service"; Flags: ignoreversion

; Add this line to include the icon file
Source: "C:\Users\Boros Titusz\University\UPT\LICENTA\desktopApp\RobotCarSecurity\RobotCarSecurity\Assets\logo.ico"; DestDir: "{app}\UI"; DestName: "RobotCar.ico"; Flags: ignoreversion

[Dirs]
Name: "{app}\Assets\Videos"

[UninstallDelete]
Type: filesandordirs; Name: "{app}\Assets\Videos"

[Icons]
Name: "{group}\RobotCar Security"; Filename: "{app}\UI\RobotCarSecurity.exe"; IconFilename: "{app}\UI\RobotCar.ico"
Name: "{commondesktop}\RobotCar Security"; Filename: "{app}\UI\RobotCarSecurity.exe"; Tasks: desktopicon; IconFilename: "{app}\UI\RobotCar.ico"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"; Flags: unchecked

[Run]
; Install service silently
Filename: "{cmd}"; Parameters: "/C ""{app}\Service\install_setup.bat"""; StatusMsg: "Installing service...";Flags: runhidden runascurrentuser