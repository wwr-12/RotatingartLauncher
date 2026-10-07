// System.Windows.Forms 最小桩（供 GOG Linux 版原版 Terraria 在启动器 .NET 10 CoreCLR 上运行）。
// 成员清单来自 Terraria.exe 元数据的 MemberRef 扫描（见 TerrariaCompatPatch 的 LegacyMemberRef 日志）。
// 行为策略：全 no-op；尺寸类返回 1920x1080、ActiveForm 返回单例、AllScreens 返回单屏，
// 与 Linux/Mono 上最小可用语义对齐。若游戏行为异常，按日志再调整返回值。
namespace System.Windows.Forms;

using System;
using System.Drawing;

public enum FormBorderStyle
{
    None = 0,
    FixedSingle = 1,
    Fixed3D = 2,
    FixedDialog = 3,
    Sizable = 4,
    FixedToolWindow = 5,
    SizableToolWindow = 6,
}

public enum FormWindowState
{
    Normal = 0,
    Minimized = 1,
    Maximized = 2,
}

public class Control
{
    public const int StubWidth = 1920;
    public const int StubHeight = 1080;

    public int Width { get => StubWidth; set { } }
    public int Height { get => StubHeight; set { } }
    public Rectangle Bounds { get => new Rectangle(0, 0, StubWidth, StubHeight); set { } }
    public Size MinimumSize { get => Size.Empty; set { } }

    public void BringToFront() { }
    public void SendToBack() { }
}

public class Form : Control
{
    private static Form? _active;

    public static Form ActiveForm => _active ??= new Form();

    public FormWindowState WindowState { get; set; } = FormWindowState.Normal;
    public FormBorderStyle FormBorderStyle { get; set; } = FormBorderStyle.None;
    public Point Location { get => new Point(0, 0); set { } }
    public Size ClientSize { get => new Size(StubWidth, StubHeight); set { } }
}

public class Screen
{
    private static Screen? _primary;

    public static Screen[] AllScreens => new[] { _primary ??= new Screen() };

    public static Screen FromPoint(Point point) => _primary ??= new Screen();

    public Rectangle Bounds => new Rectangle(0, 0, Control.StubWidth, Control.StubHeight);
    public Rectangle WorkingArea => new Rectangle(0, 0, Control.StubWidth, Control.StubHeight);
    public string DeviceName => @"\\.\DISPLAY1";
}

public static class Application
{
    public static event EventHandler? ApplicationExit
    {
        add { }
        remove { }
    }
}
