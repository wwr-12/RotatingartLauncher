using System;
using System.Collections.Generic;
using System.Reflection;
using System.Reflection.Emit;

namespace TerrariaCompatPatch;

/// <summary>
/// 动态供应 legacy 程序集桩（System.Windows.Forms / System.Data）兜底。
///
/// 主路径：随补丁发布的真实桩程序集 System.Windows.Forms.dll（真元数据，JIT 最友好），
/// 由 StartupHook.TryLoadShippedStub 加载。本类仅在发布桩缺失时兜底。
///
/// 教训（v0.1/v0.2）：在 JIT 编译期间由 TypeResolve 惰性建类型会触发 JIT 内部崩溃
///（SIGABRT，libclrjit.so）。因此兜底路径也只用于程序集级解析；若走到兜底，先在
/// Patcher 预创建阶段（Main 前）急切建类型。
/// </summary>
internal static class LegacyAssemblyStub
{
    private sealed class StubState
    {
        public AssemblyBuilder? Assembly;
        public ModuleBuilder? Module;
        public readonly Dictionary<string, Type> Types = new();
    }

    // 桩程序集集合：程序集名 → 命名空间前缀（TypeResolve 兜底路由用）
    private static readonly Dictionary<string, string> StubAssemblies = new(StringComparer.OrdinalIgnoreCase)
    {
        ["System.Windows.Forms"] = "System.Windows.Forms",
        ["System.Data"] = "System.Data",
    };

    private static readonly Dictionary<string, StubState> _states = new(StringComparer.OrdinalIgnoreCase);
    private static readonly object _lock = new();

    internal static bool IsStubAssembly(string? assemblyName) =>
        !string.IsNullOrEmpty(assemblyName) && StubAssemblies.ContainsKey(assemblyName);

    /// <summary>惰性创建并返回动态桩程序集（名为 assemblyName，v4.0.0 对齐 .NET Fx 引用）。</summary>
    internal static Assembly EnsureAssembly(string assemblyName)
    {
        lock (_lock)
        {
            var st = GetState(assemblyName);
            if (st.Assembly != null) return st.Assembly;
            var name = new AssemblyName(assemblyName) { Version = new Version(4, 0, 0, 0) };
            st.Assembly = AssemblyBuilder.DefineDynamicAssembly(name, AssemblyBuilderAccess.Run);
            st.Module = st.Assembly.DefineDynamicModule(assemblyName);
            Console.WriteLine($"[TerrariaCompatPatch] Stub assembly created: {assemblyName}");
            return st.Assembly;
        }
    }

    /// <summary>在指定桩程序集中创建裸类型（继承 object + 默认 ctor），返回其程序集。</summary>
    internal static Assembly? EnsureTypeAndReturnAssembly(string assemblyName, string fullTypeName)
    {
        try
        {
            lock (_lock)
            {
                if (fullTypeName.Contains('+')) return null; // 嵌套类型暂不处理（留待需要时）
                var st = GetState(assemblyName);
                if (st.Types.TryGetValue(fullTypeName, out var existing))
                    return existing.Assembly;

                EnsureAssembly(assemblyName);
                var tb = st.Module!.DefineType(
                    fullTypeName,
                    TypeAttributes.Public | TypeAttributes.Class | TypeAttributes.BeforeFieldInit,
                    typeof(object));
                tb.DefineDefaultConstructor(MethodAttributes.Public);
                var created = tb.CreateType();
                st.Types[fullTypeName] = created;
                return created.Assembly;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TerrariaCompatPatch] Stub create type '{assemblyName}!{fullTypeName}' failed: {ex.Message}");
            return null;
        }
    }

    /// <summary>TypeResolve 兜底：按命名空间前缀路由到对应桩程序集。</summary>
    internal static Assembly? ResolveTypeByNamespace(string? typeName)
    {
        if (string.IsNullOrEmpty(typeName)) return null;
        foreach (var kv in StubAssemblies)
        {
            if (typeName.StartsWith(kv.Value + ".", StringComparison.Ordinal))
                return EnsureTypeAndReturnAssembly(kv.Key, typeName);
        }
        return null;
    }

    private static StubState GetState(string assemblyName)
    {
        if (!_states.TryGetValue(assemblyName, out var st))
        {
            st = new StubState();
            _states[assemblyName] = st;
        }
        return st;
    }
}
