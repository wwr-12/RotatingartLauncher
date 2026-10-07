using System.Collections;
using System.Diagnostics;
using System.Net.Sockets;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using HarmonyLib;
using Terraria;
using Terraria.Localization;

namespace HostAndPlayPatch;

/// <summary>
/// Wrapper for UTF-8 string array marshaling with proper memory management
/// </summary>
public sealed class Utf8StringArrayMarshaler : IDisposable
{
    private readonly IntPtr[] _stringPointers;
    private readonly IntPtr _arrayPointer;

    public Utf8StringArrayMarshaler(string[] strings)
    {
        if (strings == null || strings.Length == 0)
        {
            _stringPointers = [];
            _arrayPointer = IntPtr.Zero;
            return;
        }

        _stringPointers = new IntPtr[strings.Length];

        for (int i = 0; i < strings.Length; i++)
        {
            byte[] utf8Bytes = Encoding.UTF8.GetBytes(strings[i] + '\0');
            _stringPointers[i] = Marshal.AllocHGlobal(utf8Bytes.Length);
            Marshal.Copy(utf8Bytes, 0, _stringPointers[i], utf8Bytes.Length);
        }

        _arrayPointer = Marshal.AllocHGlobal(IntPtr.Size * _stringPointers.Length);
        Marshal.Copy(_stringPointers, 0, _arrayPointer, _stringPointers.Length);
    }

    public IntPtr ArrayPointer => _arrayPointer;

    public void Dispose()
    {
        foreach (var ptr in _stringPointers)
        {
            if (ptr != IntPtr.Zero)
                Marshal.FreeHGlobal(ptr);
        }

        if (_arrayPointer != IntPtr.Zero)
            Marshal.FreeHGlobal(_arrayPointer);
    }
}

/// <summary>
/// Android 多进程 Host &amp; Play 补丁
/// 
/// 功能:
/// 1. Hook Main.OnSubmitServerPassword() 实现单机开服 + 连接
/// 2. 启动服务器后等待就绪再连接 (轮询端口)
/// 3. 自动处理模组差异对话框 (UIServerModsDifferMessage)
/// 4. 输出详细的模组同步和连接状态日志到 Console
/// 5. 连接超时自动处理
/// </summary>
public static class HostAndPlayPatcher
{
    private static Harmony? _harmony;

    // ── 配置 ──
    private const int DefaultPort = 7777;
    private const int ServerReadyTimeoutSeconds = 180; // 服务器加载模组+世界最多等3分钟
    private const int ServerPollIntervalMs = 1000;     // 每秒检查一次端口
    private const int PostReadyDelayMs = 2000;         // 端口打开后再等2s确保完全就绪
    private const int TileDataStuckTimeoutSeconds = 60; // 请求图格数据超时 (秒)
    private const int ConnectionMonitorTimeoutSeconds = 300; // 连接监控总超时 (5分钟)

    // ── 状态 (供外部查询，如启动器 UI) ──
    public static volatile bool ServerStarting;
    public static volatile bool ConnectionInProgress;

    // ── 当前开服信息 (用于状态显示) ──
    private static string _currentWorldName = "";
    private static int _currentModCount;
    private static string _currentModList = "";

    // ── Reflection 缓存 (用于模组差异自动处理) ──
    private static MethodInfo? _queueMainThreadAction;
    private static FieldInfo? _statusTextField;
    private static FieldInfo? _syncModHeadersField;
    private static FieldInfo? _downloadQueueField;
    private static FieldInfo? _downloadingModField;

    // ── 状态监控 ──
    private static Timer? _statusTimer;
    private static string _lastStatus = "";
    private static volatile bool _monitoring;
    private static DateTime _monitorStart;
    private const int MonitorTimeoutMinutes = 10;

    // ── P/Invoke ──
    [DllImport("main", EntryPoint = "game_launcher_launch_new_dotnet_process", CallingConvention = CallingConvention.Cdecl)]
    private static extern int NativeProcessLauncherStart(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string assemblyPath,
        int argc,
        IntPtr argv,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string title,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string gameId);

    // ═══════════════════════ 初始化 ═══════════════════════

    public static int Initialize()
    {
        try
        {
            Log("========================================");
            Log("Android Multi-Process Host & Play v2.0");
            Log("========================================");

            ApplyHarmonyPatches();

            return 0;
        }
        catch (Exception ex)
        {
            Log($"ERROR: {ex.Message}");
            return -1;
        }
    }

    private static void ApplyHarmonyPatches()
    {
        _harmony = new Harmony("com.ralaunch.hostandplay");

        var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
        var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");

        if (tModLoaderAssembly != null)
        {
            ApplyPatchesInternal(tModLoaderAssembly);
        }
        else
        {
            AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        if (args.LoadedAssembly.GetName().Name == "tModLoader")
        {
            ApplyPatchesInternal(args.LoadedAssembly);
            AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
        }
    }

    private static void ApplyPatchesInternal(Assembly assembly)
    {
        var mainType = assembly.GetType("Terraria.Main");
        if (mainType == null)
        {
            Log("ERROR: Terraria.Main not found!");
            return;
        }

        // ── 缓存 Reflection 信息 ──
        _queueMainThreadAction = mainType.GetMethod("QueueMainThreadAction",
            BindingFlags.Public | BindingFlags.Static, null, new[] { typeof(Action) }, null);
        _statusTextField = mainType.GetField("statusText",
            BindingFlags.Public | BindingFlags.Static);

        var modNetType = assembly.GetType("Terraria.ModLoader.ModNet");
        if (modNetType != null)
        {
            _syncModHeadersField = modNetType.GetField("SyncModHeaders",
                BindingFlags.NonPublic | BindingFlags.Static);
            _downloadQueueField = modNetType.GetField("downloadQueue",
                BindingFlags.NonPublic | BindingFlags.Static);
            _downloadingModField = modNetType.GetField("downloadingMod",
                BindingFlags.NonPublic | BindingFlags.Static);
        }

        // ── Patch 1: Main.OnSubmitServerPassword - 开服+连接主逻辑 ──
        var onSubmitMethod = mainType.GetMethod("OnSubmitServerPassword",
            BindingFlags.Instance | BindingFlags.NonPublic,
            null, Type.EmptyTypes, null);

        if (onSubmitMethod != null)
        {
            _harmony!.Patch(onSubmitMethod,
                prefix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(OnSubmitServerPassword_Prefix)));
            Log("[OK] Main.OnSubmitServerPassword -> Host & Play");
        }
        else
        {
            Log("WARNING: OnSubmitServerPassword() not found!");
        }

        // ── Patch 2: UIServerModsDifferMessage.Show - 自动处理模组差异 ──
        var serverModsDifferType = assembly.GetType("Terraria.ModLoader.UI.UIServerModsDifferMessage");
        var showMethod = serverModsDifferType?.GetMethod("Show",
            BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);

        if (showMethod != null)
        {
            _harmony!.Patch(showMethod,
                prefix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(ServerModsDiffer_Show_Prefix)));
            Log("[OK] UIServerModsDifferMessage.Show -> auto-accept");
        }
        else
        {
            Log("WARNING: UIServerModsDifferMessage.Show not found");
        }

        // ── Patch 3: ModNet.SyncClientMods - 记录服务器模组列表 ──
        var syncMethod = modNetType?.GetMethod("SyncClientMods",
            BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public,
            null, new[] { typeof(System.IO.BinaryReader) }, null);

        if (syncMethod != null)
        {
            _harmony!.Patch(syncMethod,
                prefix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(SyncClientMods_Prefix)),
                postfix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(SyncClientMods_Postfix)));
            Log("[OK] ModNet.SyncClientMods -> mod list logging");
        }

        // ── Patch 4: ModNet.DownloadNextMod - 记录模组下载 ──
        var downloadMethod = modNetType?.GetMethod("DownloadNextMod",
            BindingFlags.Static | BindingFlags.NonPublic);

        if (downloadMethod != null)
        {
            _harmony!.Patch(downloadMethod,
                postfix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(DownloadNextMod_Postfix)));
            Log("[OK] ModNet.DownloadNextMod -> download logging");
        }

        // ── Patch 5: ModNet.OnModsDownloaded - 记录同步完成 ──
        var downloadedMethod = modNetType?.GetMethod("OnModsDownloaded",
            BindingFlags.Static | BindingFlags.NonPublic,
            null, new[] { typeof(bool) }, null);

        if (downloadedMethod != null)
        {
            _harmony!.Patch(downloadedMethod,
                prefix: new HarmonyMethod(typeof(HostAndPlayPatcher), nameof(OnModsDownloaded_Prefix)));
            Log("[OK] ModNet.OnModsDownloaded -> completion logging");
        }

        // ── 启动状态监控 ──
        _statusTimer = new Timer(OnStatusTick, null, 2000, 2000);

        Log("All patches applied!");
        Log("========================================");
    }

    // ═══════════════════════ 服务器启动 ═══════════════════════

    /// <summary>
    /// 收集当前模组和世界信息，用于状态文本显示
    /// </summary>
    private static void CollectCurrentInfo(string worldPath)
    {
        try
        {
            // 获取世界名称
            _currentWorldName = Main.ActiveWorldFileData?.Name ?? "";
            if (string.IsNullOrEmpty(_currentWorldName))
            {
                // 从路径提取文件名 (不含扩展名)
                _currentWorldName = Path.GetFileNameWithoutExtension(worldPath);
            }
            Log($"World name: {_currentWorldName}");

            // 获取已加载的模组列表
            var loadedAssemblies = AppDomain.CurrentDomain.GetAssemblies();
            var tModLoaderAssembly = loadedAssemblies.FirstOrDefault(a => a.GetName().Name == "tModLoader");

            if (tModLoaderAssembly != null)
            {
                var modLoaderType = tModLoaderAssembly.GetType("Terraria.ModLoader.ModLoader");
                if (modLoaderType != null)
                {
                    var modsProperty = modLoaderType.GetProperty("Mods",
                        BindingFlags.Public | BindingFlags.Static);
                    if (modsProperty?.GetValue(null) is Array mods && mods.Length > 0)
                    {
                        // 过滤掉 ModLoader 自身，只显示用户模组
                        var modNames = new List<string>();
                        foreach (var mod in mods)
                        {
                            var name = mod?.GetType().GetProperty("Name")?.GetValue(mod) as string;
                            if (!string.IsNullOrEmpty(name) && name != "ModLoader")
                                modNames.Add(name);
                        }

                        _currentModCount = modNames.Count;
                        // 显示前 5 个模组名，超出部分省略
                        if (modNames.Count <= 5)
                        {
                            _currentModList = string.Join(", ", modNames);
                        }
                        else
                        {
                            _currentModList = string.Join(", ", modNames.Take(5))
                                              + $" 等 {modNames.Count} 个模组";
                        }
                        Log($"Mods ({_currentModCount}): {_currentModList}");
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log($"CollectCurrentInfo error: {ex.Message}");
            // 不影响主流程
        }
    }

    /// <summary>
    /// 启动服务器进程
    /// </summary>
    public static bool StartServerProcess(string gamePath, string worldPath, string? password, int maxPlayers)
    {
        try
        {
            Log("Building server arguments...");

            // 注意: 不要添加 -autoshutdown 参数!
            // IsPortListening 的 TCP 测试连接会被服务器视为"玩家连接后断开",
            // 导致 autoshutdown 在真正客户端连接前就关闭服务器,
            // 造成客户端在"请求图格数据"时卡住。
            var args = new List<string>
            {
                "-server",
                "-world", worldPath,
                "-maxplayers", maxPlayers.ToString(),
                "-port", DefaultPort.ToString()
            };

            if (!string.IsNullOrEmpty(password))
            {
                args.Add("-password");
                args.Add(password);
            }

            Log($"Assembly: {gamePath}");
            Log($"Args: {string.Join(" ", args)}");

            using (var marshaler = new Utf8StringArrayMarshaler(args.ToArray()))
            {
                var result = NativeProcessLauncherStart(
                    gamePath,
                    args.Count,
                    marshaler.ArrayPointer,
                    "tModLoader Server",
                    "tModLoader");

                if (result != 0)
                {
                    Log($"Native launcher returned error: {result}");
                    return false;
                }
            }

            Log("Server process launched successfully");
            return true;
        }
        catch (Exception ex)
        {
            Log($"StartServerProcess exception: {ex.Message}");
            Log($"Stack: {ex.StackTrace}");
            return false;
        }
    }

    // ═══════════════════════ 服务器就绪检测 ═══════════════════════

    /// <summary>
    /// 检查端口是否已被监听
    /// </summary>
    public static bool IsPortListening(int port)
    {
        try
        {
            using var client = new TcpClient();
            var result = client.BeginConnect("127.0.0.1", port, null, null);
            bool completed = result.AsyncWaitHandle.WaitOne(TimeSpan.FromSeconds(1));
            if (completed)
            {
                // EndConnect 会在连接失败时抛出异常（如 ConnectionRefused）
                // 只有不抛异常才说明真正连接成功 = 端口有服务在监听
                try
                {
                    client.EndConnect(result);
                    client.Close();
                    return true;
                }
                catch
                {
                    // 连接被拒绝 — 端口没有服务在监听
                    return false;
                }
            }
            return false;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// 等待服务器在指定端口上就绪
    /// 通过轮询 TCP 连接检测服务器是否已开始监听
    /// </summary>
    public static bool WaitForServerReady(int port, int timeoutSeconds)
    {
        var sw = Stopwatch.StartNew();
        int attempt = 0;

        while (sw.Elapsed.TotalSeconds < timeoutSeconds)
        {
            attempt++;

            if (IsPortListening(port))
            {
                int totalSec = (int)sw.Elapsed.TotalSeconds;
                Log($"Server ready on port {port} after {sw.Elapsed.TotalSeconds:F1}s (attempt {attempt})");
                return true;
            }

            int elapsed = (int)sw.Elapsed.TotalSeconds;

            // 根据已加载的模组数量和世界名称显示具体信息
            if (elapsed < 30)
            {
                string modInfo = _currentModCount > 0
                    ? $"服务器正在加载 {_currentModCount} 个模组...\n{_currentModList}"
                    : "服务器正在加载模组...";
                Main.statusText = $"{modInfo} ({elapsed}s)";
            }
            else if (elapsed < 90)
            {
                string worldInfo = !string.IsNullOrEmpty(_currentWorldName)
                    ? $"服务器正在加载世界: {_currentWorldName}"
                    : "服务器正在加载世界...";
                Main.statusText = $"{worldInfo} ({elapsed}s)";
            }
            else
            {
                Main.statusText = $"服务器仍在启动中，请耐心等待... ({elapsed}s)";
            }

            if (attempt % 10 == 0)
                Log($"Waiting for server... ({elapsed}s, attempt {attempt})");

            Thread.Sleep(ServerPollIntervalMs);
        }

        Log($"Server did not become ready within {timeoutSeconds}s");
        return false;
    }

    // ═══════════════════════ Patch 1: 开服+连接主逻辑 ═══════════════════════

    /// <summary>
    /// Main.OnSubmitServerPassword 前缀补丁
    /// 替换为: 启动服务器 → 等待就绪 → 连接
    /// </summary>
    public static bool OnSubmitServerPassword_Prefix(Main __instance)
    {
        Log("========================================");
        Log("Host & Play 请求");
        Log("========================================");

        try
        {
            // ── 获取世界路径 ──
            string worldPath;
            if (Main.ActiveWorldFileData != null && !string.IsNullOrEmpty(Main.ActiveWorldFileData.Path))
                worldPath = Main.ActiveWorldFileData.Path;
            else if (!string.IsNullOrEmpty(Main.worldPathName))
                worldPath = Main.worldPathName;
            else
            {
                Log("ERROR: No world selected!");
                Main.statusText = "错误: 未选择世界";
                Main.menuMode = 0;
                return false;
            }

            // ── 获取游戏程序集路径 ──
            string gamePath = Path.Combine(
                Path.GetDirectoryName(typeof(Main).Assembly.Location) ?? "",
                "tModLoader.dll");

            Log($"Game: {gamePath}");
            Log($"World: {worldPath}");

            // ── 收集当前信息用于状态显示 ──
            CollectCurrentInfo(worldPath);

            // ── 验证文件存在 ──
            if (!File.Exists(worldPath))
            {
                Log($"ERROR: World file not found: {worldPath}");
                Main.statusText = "错误: 世界文件不存在";
                Main.menuMode = 0;
                return false;
            }

            // ── 启动服务器进程 ──
            Main.statusText = "正在启动服务器进程...";
            Main.menuMode = 14; // 状态显示界面

            var success = StartServerProcess(
                gamePath, worldPath, Netplay.ServerPassword, Main.maxNetPlayers);

            if (!success)
            {
                Log("Failed to start server process!");
                Main.statusText = "服务器启动失败，请检查日志";
                Thread.Sleep(100); // 让 UI 更新
                Main.menuMode = 0;
                return false;
            }

            // ── 启动后台线程: 等待服务器就绪 → 连接 ──
            ServerStarting = true;
            ConnectionInProgress = true;
            StartMonitoring();

            var connectThread = new Thread(WaitForServerAndConnect)
            {
                IsBackground = true,
                Name = "HostAndPlay-WaitConnect"
            };
            connectThread.Start();

            Log("Background connect thread started");
            Log("========================================");
            return false;
        }
        catch (Exception ex)
        {
            Log($"ERROR: {ex.Message}");
            Log($"Stack: {ex.StackTrace}");
            Main.statusText = $"错误: {ex.Message}";
            Main.menuMode = 0;
            return false;
        }
    }

    /// <summary>
    /// 后台线程: 等待服务器就绪后连接，并监控连接过程
    /// </summary>
    private static void WaitForServerAndConnect()
    {
        try
        {
            Log("Waiting for server to start listening...");

            // ── 阶段 1: 等待服务器端口就绪 ──
            bool ready = WaitForServerReady(DefaultPort, ServerReadyTimeoutSeconds);
            ServerStarting = false;

            if (!ready)
            {
                Log("Server startup timeout!");
                Main.statusText = $"服务器启动超时 ({ServerReadyTimeoutSeconds}秒)，请检查服务器日志";
                Thread.Sleep(5000); // 让用户看到错误
                ConnectionInProgress = false;
                StopMonitoring();
                Main.menuMode = 0;
                return;
            }

            // ── 阶段 2: 短暂等待确保服务器完全初始化 ──
            Log($"Port {DefaultPort} is open, waiting {PostReadyDelayMs}ms for full init...");
            Main.statusText = "服务器已就绪，准备连接...";
            Thread.Sleep(PostReadyDelayMs);

            // ── 阶段 3: 连接 ──
            Log("Connecting to local server...");
            ConnectToLocalServer();

            // ── 阶段 4: 监控连接进度 ──
            MonitorConnectionProgress();
        }
        catch (Exception ex)
        {
            Log($"WaitForServerAndConnect error: {ex.Message}");
            Log($"Stack: {ex.StackTrace}");
            Main.statusText = $"连接失败: {ex.Message}";
            ConnectionInProgress = false;
            StopMonitoring();
            Thread.Sleep(3000);
            Main.menuMode = 0;
        }
    }

    /// <summary>
    /// 监控连接过程，检测卡住和超时
    /// 
    /// tModLoader 客户端连接状态流程 (基于源码 Netplay.cs):
    ///   State 0→1: "发现服务器" (Found server)
    ///   State 1→2: "正在发送玩家数据..." (Sending player data)
    ///   [模组同步]: "正在同步模组..." (Syncing mods, 可能触发模组下载和重载)
    ///   State 2→3: "正在请求世界信息" (Requesting world info)
    ///   State 3→4: 加载地图数据 (Loading map data)
    ///   State 4→5: 绘制地图 (Drawing map)
    ///   State 5→6: "正在请求图格数据" (Requesting tile data)
    ///   State 6:   接收图格数据 (X%) → 进入游戏
    /// </summary>
    private static void MonitorConnectionProgress()
    {
        var sw = Stopwatch.StartNew();
        string lastStatus = "";
        DateTime lastStatusChange = DateTime.UtcNow;
        string lastLoggedPhase = "";

        Log("========================================");
        Log("开始监控连接进度...");
        Log("========================================");

        while (sw.Elapsed.TotalSeconds < ConnectionMonitorTimeoutSeconds)
        {
            Thread.Sleep(500);

            try
            {
                string status = (_statusTextField?.GetValue(null) as string) ?? Main.statusText ?? "";

                // ── 检测状态变化 ──
                if (!string.IsNullOrEmpty(status) && status != lastStatus)
                {
                    lastStatus = status;
                    lastStatusChange = DateTime.UtcNow;

                    // 识别并记录连接阶段
                    string phase = IdentifyConnectionPhase(status);
                    if (phase != lastLoggedPhase)
                    {
                        Log($"连接阶段: [{phase}] {status}");
                        lastLoggedPhase = phase;
                    }
                }

                double stuckSeconds = (DateTime.UtcNow - lastStatusChange).TotalSeconds;

                // ── 检测 "请求图格数据" 卡住 ──
                if (IsRequestingTileData(lastStatus))
                {
                    if (stuckSeconds > TileDataStuckTimeoutSeconds)
                    {
                        Log($"ERROR: 请求图格数据已超时! 卡住 {stuckSeconds:F0}s");
                        Log("可能原因: 服务器进程已退出、网络连接中断、或服务器无响应");
                        Main.statusText = $"请求图格数据超时 ({(int)stuckSeconds}s)\n服务器可能已断开，正在返回菜单...";
                        Thread.Sleep(4000);
                        ResetToMenu("请求图格数据超时");
                        return;
                    }
                    else if (stuckSeconds > 15)
                    {
                        // 提示用户正在等待，但不覆盖 tModLoader 正常的进度显示
                        Log($"请求图格数据中... 已等待 {(int)stuckSeconds}s");
                    }
                }

                // ── 检测连接丢失 ──
                if (IsConnectionLost(lastStatus))
                {
                    Log($"检测到连接断开: {lastStatus}");
                    ConnectionInProgress = false;
                    StopMonitoring();
                    return; // tModLoader 会自己处理 UI
                }

                // ── 检测成功进入游戏 ──
                if (Main.netMode == 1 && Main.menuMode == 0 && Main.gameMenu == false)
                {
                    Log("========================================");
                    Log($"成功进入游戏! 总连接耗时: {sw.Elapsed.TotalSeconds:F1}s");
                    Log("========================================");
                    ConnectionInProgress = false;
                    StopMonitoring();
                    return;
                }

                // ── 检测回到主菜单 (连接失败) ──
                if (Main.menuMode == 0 && Main.netMode == 0 && sw.Elapsed.TotalSeconds > 5)
                {
                    Log("检测到已返回主菜单，连接可能失败");
                    ConnectionInProgress = false;
                    StopMonitoring();
                    return;
                }

                // ── 检测单阶段超时 (非图格数据阶段) ──
                if (stuckSeconds > 120 && !IsReceivingTileData(lastStatus))
                {
                    Log($"WARNING: 当前阶段已停滞 {stuckSeconds:F0}s: {lastStatus}");
                }
            }
            catch (Exception ex)
            {
                Log($"MonitorConnectionProgress tick error: {ex.Message}");
            }
        }

        // ── 总超时 ──
        Log($"连接监控总超时 ({ConnectionMonitorTimeoutSeconds}s)");
        ResetToMenu("连接超时");
    }

    /// <summary>
    /// 识别当前连接阶段 (基于 tModLoader 状态文本)
    /// </summary>
    private static string IdentifyConnectionPhase(string status)
    {
        if (string.IsNullOrEmpty(status)) return "未知";

        // tModLoader 连接阶段 (中英文兼容)
        if (status.Contains("发现服务器") || status.Contains("Found server"))
            return "发现服务器";
        if (status.Contains("发送玩家数据") || status.Contains("Sending player"))
            return "发送玩家数据";
        if (status.Contains("同步模组") || status.Contains("Syncing") || status.Contains("SyncMods"))
            return "同步模组";
        if (status.Contains("请求世界信息") || status.Contains("Requesting world"))
            return "请求世界信息";
        if (status.Contains("加载地图") || status.Contains("Loading map"))
            return "加载地图数据";
        if (status.Contains("绘制地图") || status.Contains("Drawing map"))
            return "绘制地图";
        if (status.Contains("请求图格") || status.Contains("Requesting tile"))
            return "请求图格数据";
        if (status.Contains("接收") || status.Contains("Receiving") || status.Contains("%"))
            return "接收数据";
        if (status.Contains("断开") || status.Contains("Lost connection") || status.Contains("Disconnected"))
            return "连接断开";
        if (status.Contains("重新加载") || status.Contains("Reload") || status.Contains("reload"))
            return "重新加载模组";
        if (status.Contains("下载") || status.Contains("Download") || status.Contains("download"))
            return "下载模组";

        return "其他";
    }

    /// <summary>
    /// 检查是否处于 "请求图格数据" 状态 (已发送请求但未收到数据)
    /// </summary>
    private static bool IsRequestingTileData(string status)
    {
        if (string.IsNullOrEmpty(status)) return false;
        return (status.Contains("请求图格") || status.Contains("Requesting tile"))
               && !status.Contains("%"); // 有百分比说明正在接收，不算卡住
    }

    /// <summary>
    /// 检查是否正在接收图格数据 (有进度百分比)
    /// </summary>
    private static bool IsReceivingTileData(string status)
    {
        if (string.IsNullOrEmpty(status)) return false;
        return status.Contains("%") &&
               (status.Contains("图格") || status.Contains("tile") || status.Contains("Tile")
                || status.Contains("接收") || status.Contains("Receiving"));
    }

    /// <summary>
    /// 检查连接是否已断开
    /// </summary>
    private static bool IsConnectionLost(string status)
    {
        if (string.IsNullOrEmpty(status)) return false;
        return status.Contains("Lost connection") || status.Contains("断开连接")
               || status.Contains("Connection lost") || status.Contains("连接已断开")
               || status.Contains("Disconnected from server");
    }

    /// <summary>
    /// 重置到主菜单
    /// </summary>
    private static void ResetToMenu(string reason)
    {
        Log($"ResetToMenu: {reason}");
        ConnectionInProgress = false;
        StopMonitoring();

        try
        {
            // 断开网络连接
            if (Main.netMode != 0)
            {
                Main.netMode = 0;
                Netplay.Disconnect = true;
            }
        }
        catch (Exception ex)
        {
            Log($"ResetToMenu disconnect error: {ex.Message}");
        }

        Main.menuMode = 0;
    }

    /// <summary>
    /// 执行客户端连接到 127.0.0.1:7777
    /// </summary>
    private static void ConnectToLocalServer()
    {
        Log("Connecting to 127.0.0.1:7777...");
        Main.netMode = 1;
        Netplay.SetRemoteIP("127.0.0.1");
        Main.autoPass = true;
        Main.statusText = "正在连接到本地服务器...";

        // StartTcpClient 创建新的 TCP 客户端线程
        // TcpClientLoop -> ClientLoopSetup -> InnerClientLoop
        Netplay.StartTcpClient();
        Main.menuMode = 14;

        Log("Client TCP connection started");
    }

    // ═══════════════════════ Patch 2: 自动处理模组差异 ═══════════════════════
    // 
    // 当 Client 连接到 Server 后, 如果模组列表不一致,
    // tModLoader 会弹出 UIServerModsDifferMessage 对话框,
    // 需要用户手动点击 "重新加载并继续"。
    // 在 Android 上这个 UI 可能无法交互, 导致连接永远卡住。
    // 
    // 此补丁自动执行 continueButtonAction (等效于点击"继续")。

    /// <summary>
    /// UIServerModsDifferMessage.Show 前缀补丁
    /// 自动接受模组变更，跳过 UI 对话框
    /// 
    /// 原始签名:
    ///   void Show(string message, int gotoMenu, UIState gotoState,
    ///             string continueButtonText, Action continueButtonAction,
    ///             string backButtonText, Action backButtonAction,
    ///             List&lt;ReloadRequiredExplanation&gt; reloadRequiredExplanationEntries)
    /// </summary>
    public static bool ServerModsDiffer_Show_Prefix(
        string message,
        Action continueButtonAction,
        object reloadRequiredExplanationEntries)
    {
        try
        {
            Log("========================================");
            Log("检测到服务器模组差异 - 自动处理");
            Log("========================================");

            // ── 输出模组差异详情 ──
            if (reloadRequiredExplanationEntries is IEnumerable entries)
            {
                int count = 0;
                foreach (var entry in entries)
                {
                    try
                    {
                        var t = entry.GetType();
                        var mod = t.GetProperty("mod")?.GetValue(entry) as string ?? "Unknown";
                        var reason = t.GetProperty("reason")?.GetValue(entry) as string ?? "";
                        int typeOrder = 0;
                        try { typeOrder = (int)(t.GetProperty("typeOrder")?.GetValue(entry) ?? 0); }
                        catch { /* ignore */ }

                        string action = typeOrder switch
                        {
                            1 => "下载",
                            2 => "版本切换",
                            3 => "启用",
                            4 => "禁用",
                            5 => "配置变更",
                            _ => "变更"
                        };

                        Log($"  [{action}] {mod}: {CleanChatTags(reason)}");
                        count++;
                    }
                    catch { Log("  (条目读取失败)"); }
                }
                Log($"共 {count} 个模组需要变更");
            }

            // ── 自动触发继续操作 ──
            if (continueButtonAction != null)
            {
                Log("自动接受模组变更，开始同步...");

                if (_queueMainThreadAction != null)
                {
                    _queueMainThreadAction.Invoke(null, new object[] { continueButtonAction });
                    Log("模组同步操作已队列化到主线程");
                }
                else
                {
                    Log("WARNING: QueueMainThreadAction 不可用，直接执行");
                    continueButtonAction.Invoke();
                }
            }
            else
            {
                Log("WARNING: continueButtonAction 为 null，无法自动继续");
            }

            Log("========================================");
            return false; // 跳过原始方法 (不显示 UI)
        }
        catch (Exception ex)
        {
            Log($"ServerModsDiffer_Show_Prefix 异常: {ex.Message}");
            return true; // 出错时回退到原始方法
        }
    }

    // ═══════════════════════ Patch 3: 模组同步日志 ═══════════════════════

    public static void SyncClientMods_Prefix()
    {
        Log("========================================");
        Log("开始同步服务器模组列表...");
        StartMonitoring();
    }

    public static void SyncClientMods_Postfix()
    {
        try
        {
            // 输出服务器模组列表
            var headers = _syncModHeadersField?.GetValue(null) as IList;
            if (headers == null || headers.Count == 0)
            {
                Log("服务器没有需要同步的模组");
                return;
            }

            Log($"服务器模组列表 ({headers.Count} 个):");
            foreach (var header in headers)
            {
                try
                {
                    var ht = header.GetType();
                    var name = ht.GetField("name")?.GetValue(header) as string ?? "?";
                    var version = ht.GetField("version")?.GetValue(header)?.ToString() ?? "?";
                    Log($"  - {name} v{version}");
                }
                catch { Log("  - (读取失败)"); }
            }
            Log("========================================");
        }
        catch (Exception ex)
        {
            Log($"SyncClientMods_Postfix error: {ex.Message}");
        }
    }

    // ═══════════════════════ Patch 4: 模组下载日志 ═══════════════════════

    public static void DownloadNextMod_Postfix()
    {
        try
        {
            var mod = _downloadingModField?.GetValue(null);
            if (mod == null) return;

            var name = mod.GetType().GetField("name")?.GetValue(mod) as string ?? "?";
            int remaining = (_downloadQueueField?.GetValue(null) as ICollection)?.Count ?? 0;
            Log($"正在下载模组: {name} (剩余 {remaining} 个待下载)");
        }
        catch (Exception ex)
        {
            Log($"DownloadNextMod_Postfix error: {ex.Message}");
        }
    }

    // ═══════════════════════ Patch 5: 同步完成日志 ═══════════════════════

    public static void OnModsDownloaded_Prefix(bool needsReload)
    {
        Log("========================================");
        if (needsReload)
        {
            Log("模组下载/变更完成，正在重新加载模组...");
            Log("请耐心等待，重新加载可能需要较长时间");
        }
        else
        {
            Log("模组同步完成，正在加入服务器...");
            ConnectionInProgress = false;
            StopMonitoring();
        }
        Log("========================================");
    }

    // ═══════════════════════ 状态文本监控 ═══════════════════════

    private static void StartMonitoring()
    {
        _monitoring = true;
        _monitorStart = DateTime.UtcNow;
        _lastStatus = "";
    }

    private static void StopMonitoring()
    {
        _monitoring = false;
    }

    private static void OnStatusTick(object? state)
    {
        if (!_monitoring) return;

        // 超时自动停止
        if ((DateTime.UtcNow - _monitorStart).TotalMinutes > MonitorTimeoutMinutes)
        {
            _monitoring = false;
            return;
        }

        try
        {
            var status = _statusTextField?.GetValue(null) as string
                         ?? Main.statusText;

            if (!string.IsNullOrWhiteSpace(status) && status != _lastStatus)
            {
                _lastStatus = status;
                Log($"状态: {status}");
            }
        }
        catch { /* 静默忽略 */ }
    }

    // ═══════════════════════ 工具方法 ═══════════════════════

    /// <summary>
    /// 清理 Terraria 聊天颜色标签 [c/RRGGBB:text] → text
    /// </summary>
    private static string CleanChatTags(string text)
    {
        if (string.IsNullOrEmpty(text)) return text;
        return Regex.Replace(text, @"\[c/[0-9A-Fa-f]{6}:([^\]]*)\]", "$1");
    }

    private static void Log(string msg) => Console.WriteLine($"[HostAndPlayPatch] {msg}");
}
