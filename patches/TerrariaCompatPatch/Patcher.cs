using HarmonyLib;
using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;

namespace TerrariaCompatPatch;

/// <summary>
/// 原版 Terraria 兼容补丁。
///
/// 目标：让 GOG Linux 版 Terraria.exe（.NET Framework 4.0 + FNA IL）在启动器 .NET 10 CoreCLR 上运行。
///
/// 职责：
///   1) 用 System.Reflection.Metadata 扫描 Terraria.exe 的 AssemblyRef/TypeRef/MemberRef 表：
///      - 随补丁发布了真实桩（zip 内 System.Windows.Forms.dll）→ 跳过动态 Emit；
///      - 否则急切预建顶层 stub 类型（Main 运行前，任何 Terraria 方法被 JIT 之前；
///        v0.1 的惰性 TypeResolve 在 JIT 编译中建类型会触发 clrjit SIGABRT）；
///   2) 打日志记录 Terraria 引用的每个 legacy 成员 —— 迭代补成员桩的依据；
///   3) 对设备迭代发现的具体失败 API 做弱类型 Harmony 打桩（Phase 3 入口）。
/// </summary>
public static class Patcher
{
    private static Harmony? _harmony;
    private const string HarmonyId = "com.ralaunch.terraria.compat";

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        try
        {
            Console.WriteLine("========================================");
            Console.WriteLine("[TerrariaCompatPatch] v0.3.0 vanilla-Terraria-on-CoreCLR compat patch...");
            Console.WriteLine("========================================");

            _harmony = new Harmony(HarmonyId);

            // Terraria 程序集在启动钩子阶段已加载（hostfxr 预载应用程序集以定位入口点）；
            // Main 尚未执行 —— 正是急切预建桩类型的时机（任何 Terraria 方法被 JIT 之前）。
            var terraria = FindLoadedAssembly("Terraria");
            if (terraria != null)
            {
                PreCreateLegacyStubTypes(terraria);
                ApplyPatchesInternal(terraria);
            }
            else
            {
                Console.WriteLine("[TerrariaCompatPatch] Terraria assembly not yet loaded; subscribing AssemblyLoad");
                AppDomain.CurrentDomain.AssemblyLoad += OnAssemblyLoaded;
            }

            Console.WriteLine("[TerrariaCompatPatch] Patch initialized successfully");
            Console.WriteLine("========================================");
            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaCompatPatch] ERROR: {ex.Message}");
            Console.WriteLine($"[TerrariaCompatPatch] Stack: {ex.StackTrace}");
            return -1;
        }
    }

    private static void OnAssemblyLoaded(object? sender, AssemblyLoadEventArgs args)
    {
        try
        {
            if (args.LoadedAssembly.GetName().Name == "Terraria")
            {
                AppDomain.CurrentDomain.AssemblyLoad -= OnAssemblyLoaded;
                PreCreateLegacyStubTypes(args.LoadedAssembly);
                ApplyPatchesInternal(args.LoadedAssembly);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaCompatPatch] OnAssemblyLoaded error: {ex.Message}");
        }
    }

    /// <summary>
    /// 扫描 Terraria.exe 元数据：记录 legacy 引用清单；无发布桩时急切预建 stub 类型。
    /// </summary>
    private static void PreCreateLegacyStubTypes(Assembly terrariaAssembly)
    {
        try
        {
            var path = terrariaAssembly.Location;
            if (string.IsNullOrEmpty(path) || !File.Exists(path))
            {
                Console.WriteLine($"[TerrariaCompatPatch] Terraria location unavailable: '{path}'");
                return;
            }

            using var fs = File.OpenRead(path);
            using var pe = new PEReader(fs);
            var md = pe.GetMetadataReader();

            // 1) 收集 legacy 程序集的 AssemblyReference handle；随补丁发布了真实桩（zip 内 DLL）的不再动态 Emit
            string patchDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
            var legacyRefs = new HashSet<EntityHandle>();
            var emitNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var arh in md.AssemblyReferences)
            {
                var ar = md.GetAssemblyReference(arh);
                var asmName = md.GetString(ar.Name);
                if (!LegacyAssemblyStub.IsStubAssembly(asmName)) continue;
                legacyRefs.Add((EntityHandle)arh);
                bool shipped = File.Exists(Path.Combine(patchDir, asmName + ".dll"));
                Console.WriteLine($"[TerrariaCompatPatch] Legacy AssemblyRef: {asmName} (shipped stub: {shipped})");
                if (!shipped) emitNames.Add(asmName);
            }

            // 2) 预建这些程序集里被引用的类型（顶层；嵌套 TypeRef 的 scope 是父 TypeRef，另行统计）
            int created = 0, nestedRefs = 0;
            var legacyTypeRefs = new HashSet<TypeReferenceHandle>();
            foreach (var trh in md.TypeReferences)
            {
                var tr = md.GetTypeReference(trh);
                var scope = tr.ResolutionScope;
                if (scope.IsNil) continue;
                if (scope.Kind == HandleKind.AssemblyReference)
                {
                    if (!legacyRefs.Contains(scope)) continue;
                    legacyTypeRefs.Add(trh);
                    var full = TypeRefFullName(md, tr);
                    var refAsmName = AssemblyNameForScope(md, scope);
                    if (emitNames.Contains(refAsmName) &&
                        LegacyAssemblyStub.EnsureTypeAndReturnAssembly(refAsmName, full) != null)
                        created++;
                }
                else if (scope.Kind == HandleKind.TypeReference)
                {
                    nestedRefs++;
                }
            }

            // 3) 记录 legacy 成员引用清单（无发布桩时是下一轮补成员桩的依据；发布桩时用于核对覆盖度）
            int memberRefs = 0;
            foreach (var mrh in md.MemberReferences)
            {
                var mr = md.GetMemberReference(mrh);
                if (mr.Parent.Kind != HandleKind.TypeReference) continue;
                var trh = (TypeReferenceHandle)mr.Parent;
                if (!legacyTypeRefs.Contains(trh)) continue;
                var tr = md.GetTypeReference(trh);
                Console.WriteLine($"[TerrariaCompatPatch] LegacyMemberRef: {TypeRefFullName(md, tr)}::{md.GetString(mr.Name)}");
                memberRefs++;
            }

            Console.WriteLine($"[TerrariaCompatPatch] Pre-created {created} stub types; " +
                              $"{memberRefs} legacy member refs logged; nested TypeRefs={nestedRefs}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaCompatPatch] PreCreateLegacyStubTypes failed: {ex.Message}");
            Console.WriteLine($"[TerrariaCompatPatch] Stack: {ex.StackTrace}");
        }
    }

    private static string TypeRefFullName(MetadataReader md, TypeReference tr)
    {
        var ns = tr.Namespace.IsNil ? "" : md.GetString(tr.Namespace);
        var name = md.GetString(tr.Name);
        return ns.Length == 0 ? name : $"{ns}.{name}";
    }

    private static string AssemblyNameForScope(MetadataReader md, EntityHandle assemblyRefScope)
    {
        // 调用方已确保 scope 是 legacy AssemblyReference；取其程序集名
        var ar = md.GetAssemblyReference((AssemblyReferenceHandle)assemblyRefScope);
        return md.GetString(ar.Name);
    }

    /// <summary>
    /// Phase 3 入口：对设备迭代发现的具体失败 API 做弱类型 Harmony 打桩。
    /// 模板见 ConsolePatch / MonoGameGLESPatch：asm.GetType("...") + AccessTools.Method + _harmony.Patch(prefix: ...)。
    /// </summary>
    private static void ApplyPatchesInternal(Assembly terrariaAssembly)
    {
        try
        {
            Console.WriteLine($"[TerrariaCompatPatch] Terraria assembly located: {terrariaAssembly.GetName().Name}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaCompatPatch] ApplyPatchesInternal error: {ex.Message}");
        }
    }

    private static Assembly? FindLoadedAssembly(string simpleName)
    {
        foreach (var a in AppDomain.CurrentDomain.GetAssemblies())
        {
            if (a.GetName().Name == simpleName) return a;
        }
        return null;
    }
}
