using System;
using System.IO;
using System.Reflection;
using TerrariaCompatPatch;

/// <summary>
/// DOTNET_STARTUP_HOOKS 入口类
/// 此类必须在全局命名空间（没有命名空间），会在应用程序 Main() 之前自动执行。
/// </summary>
internal class StartupHook
{
    /// <summary>
    /// DOTNET_STARTUP_HOOKS 要求的初始化方法（无参、返回 void）。
    /// </summary>
    public static void Initialize()
    {
        Console.WriteLine("[StartupHook] TerrariaCompatPatch DOTNET_STARTUP_HOOKS executing...");

        // 程序集解析：从 MONOMOD_PATH 载依赖；并为 legacy 程序集（WinForms/System.Data）供应桩
        AppDomain.CurrentDomain.AssemblyResolve += OnAssemblyResolve;
        // 类型解析兜底：legacy 命名空间内的类型按需在桩程序集中创建
        //（正常路径是随补丁发布的真实桩程序集；动态 Emit 仅作兜底）
        AppDomain.CurrentDomain.TypeResolve += OnTypeResolve;

        // 调用补丁初始化方法
        int result = TerrariaCompatPatch.Patcher.Initialize(IntPtr.Zero, 0);
        Console.WriteLine($"[StartupHook] Patcher.Initialize returned: {result}");
    }

    private static Assembly? OnAssemblyResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            string assemblyName = new AssemblyName(args.Name).Name ?? "";

            // 原版 Terraria 静态引用 System.Windows.Forms / System.Data；
            // 安卓/.NET 10 无这些程序集。优先加载随补丁发布的真实桩程序集
            //（真元数据，JIT 最友好）；找不到再回退动态 Emit。
            if (LegacyAssemblyStub.IsStubAssembly(assemblyName))
            {
                var shipped = TryLoadShippedStub(assemblyName);
                if (shipped != null) return shipped;
                return LegacyAssemblyStub.EnsureAssembly(assemblyName);
            }

            // 优先从 MONOMOD_PATH 环境变量指定的目录加载依赖（Harmony/MonoMod 等）
            string? monoModPath = Environment.GetEnvironmentVariable("MONOMOD_PATH");
            if (!string.IsNullOrEmpty(monoModPath))
            {
                string monoModAssemblyPath = Path.Combine(monoModPath, assemblyName + ".dll");
                if (File.Exists(monoModAssemblyPath))
                {
                    Console.WriteLine($"[StartupHook] Loading dependency from MONOMOD_PATH: {assemblyName}");
                    return Assembly.LoadFrom(monoModAssemblyPath);
                }
            }

            // 备选: 从补丁自己的目录加载
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string localAssemblyPath = Path.Combine(patchDir, assemblyName + ".dll");
            if (File.Exists(localAssemblyPath))
            {
                Console.WriteLine($"[StartupHook] Loading local dependency: {assemblyName}");
                return Assembly.LoadFrom(localAssemblyPath);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[StartupHook] Failed to resolve assembly {args.Name}: {ex.Message}");
        }

        return null;
    }

    /// <summary>加载随补丁发布（zip 内）的 legacy 桩程序集，如 System.Windows.Forms.dll。</summary>
    private static Assembly? TryLoadShippedStub(string assemblyName)
    {
        try
        {
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            string?[] roots = { patchDir, Environment.GetEnvironmentVariable("MONOMOD_PATH") };
            foreach (var root in roots)
            {
                if (string.IsNullOrEmpty(root)) continue;
                string p = Path.Combine(root, assemblyName + ".dll");
                if (File.Exists(p))
                {
                    Console.WriteLine($"[StartupHook] Loading shipped legacy stub: {p}");
                    return Assembly.LoadFrom(p);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[StartupHook] TryLoadShippedStub({assemblyName}) failed: {ex.Message}");
        }
        return null;
    }

    private static Assembly? OnTypeResolve(object? sender, ResolveEventArgs args)
    {
        try
        {
            return LegacyAssemblyStub.ResolveTypeByNamespace(args.Name);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[StartupHook] TypeResolve failed for {args.Name}: {ex.Message}");
        }
        return null;
    }
}
