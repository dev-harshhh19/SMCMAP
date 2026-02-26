[Setup]
AppId={{DA4A92E0-12A5-42EF-A1B9-72BEFD3D4F5A}
AppName=SMCMAP
AppVersion=1.0
AppPublisher=HARSHAD NIKAM
DefaultDirName={autopf}\SMCMAP
DefaultGroupName=SMCMAP
DisableProgramGroupPage=yes
OutputDir=installer_build
OutputBaseFilename=SMCMAP_Setup_v1.0
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "portable_build\SMCMAP\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\SMCMAP"; Filename: "{app}\SMCMAP.exe"
Name: "{autodesktop}\SMCMAP"; Filename: "{app}\SMCMAP.exe"; Tasks: desktopicon

[Registry]
; Force the application to ALWAYS run as Administrator when launched
Root: "HKLM"; Subkey: "Software\Microsoft\Windows NT\CurrentVersion\AppCompatFlags\Layers"; ValueType: String; ValueName: "{app}\SMCMAP.exe"; ValueData: "~ RUNASADMIN"; Flags: uninsdeletekeyifempty

[Run]
Filename: "{app}\SMCMAP.exe"; Description: "Launch SMCMAP (As Administrator)"; Flags: nowait postinstall shellexec
