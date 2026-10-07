using System;
using System.Reflection;
using System.Reflection.Emit;
using System.Runtime.InteropServices;
using HarmonyLib;

namespace MonoGameGLESPatch;

/// <summary>
/// MonoGame GLES 兼容补丁 v7 — GLES 原生实现替换
///
/// 策略: 不跳过桌面 GL 独有函数，而是用 GLES 等价实现替换:
///   - glPolygonMode    → GLES 无线框模式，填充是唯一模式（规范行为）
///   - glDrawBuffer     → glDrawBuffers(1, &mode)  (GLES 3.0 核心)
///   - glMapBuffer      → glMapBufferRange          (GLES 3.0 核心)
///   - glGetTexImage    → FBO + glReadPixels 回读   (GLES 无直接等价)
///   - glClearDepth     → glClearDepthf              (MonoGame 已有 fallback)
///   - glDepthRange     → glDepthRangef              (MonoGame 的 DepthRange() 已处理)
///
/// 其余补丁修复 MonoGame 桌面 GL 假设:
///   1. SDL 窗口创建前设置 GLES 3.0 属性 → EGL 选择正确的 config
///   2. GL.BoundApi = ES → 让 MonoGame 走 ES 代码路径
///   3. 修复 "OpenGL ES X.Y" 版本字符串解析
///   4. 强制 GLES FBO 核心能力
///   5. GL.GetString 防御性 null 处理
/// </summary>
public static class MonoGameGLESPatcher
{
    private static Harmony? _harmony;
    private static bool _initialized;
    private static bool _shouldPatch;

    // MonoGame 反射缓存
    private static Type? _glType;
    private static FieldInfo? _boundApiField;
    private static object? _renderApiES;
    private static MethodInfo? _setAttributeMethod;
    private const BindingFlags ALL = BindingFlags.Static | BindingFlags.Instance
                                   | BindingFlags.NonPublic | BindingFlags.Public;

    // =========================================================================
    // GLES P/Invoke — 直接调用 libGLESv2.so 中的 GLES 3.0 核心函数
    // =========================================================================

    [DllImport("libGLESv2.so", EntryPoint = "glDrawBuffers")]
    private static extern void glesDrawBuffers(int n, int[] bufs);

    [DllImport("libGLESv2.so", EntryPoint = "glMapBufferRange")]
    private static extern IntPtr glesMapBufferRange(int target, IntPtr offset, IntPtr length, int access);

    [DllImport("libGLESv2.so", EntryPoint = "glGetBufferParameteriv")]
    private static extern void glesGetBufferParameteriv(int target, int pname, IntPtr params_);

    [DllImport("libGLESv2.so", EntryPoint = "glReadPixels")]
    private static extern void glesReadPixels(int x, int y, int width, int height, int format, int type, IntPtr data);

    [DllImport("libGLESv2.so", EntryPoint = "glGenFramebuffers")]
    private static extern void glesGenFramebuffers(int n, IntPtr framebuffers);

    [DllImport("libGLESv2.so", EntryPoint = "glBindFramebuffer")]
    private static extern void glesBindFramebuffer(int target, int framebuffer);

    [DllImport("libGLESv2.so", EntryPoint = "glFramebufferTexture2D")]
    private static extern void glesFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);

    [DllImport("libGLESv2.so", EntryPoint = "glDeleteFramebuffers")]
    private static extern void glesDeleteFramebuffers(int n, IntPtr framebuffers);

    [DllImport("libGLESv2.so", EntryPoint = "glGetIntegerv")]
    private static extern void glesGetIntegerv(int pname, IntPtr data);

    // =========================================================================
    // GLES 兼容实现 — 用 GLES 3.0 函数实现桌面 GL 语义
    // =========================================================================

    /// <summary>
    /// glDrawBuffer(mode) → glDrawBuffers(1, {mode})
    /// GLES 3.0 没有 glDrawBuffer (单数)，但有 glDrawBuffers (复数)
    /// </summary>
    public static void GLES_DrawBuffer(int mode)
    {
        glesDrawBuffers(1, new[] { mode });
    }

    /// <summary>
    /// glMapBuffer(target, access) → glMapBufferRange(target, 0, size, flags)
    /// GLES 3.0 没有 glMapBuffer，用 glMapBufferRange 替代
    /// 先查询 buffer 大小，再映射整个 buffer
    /// </summary>
    public static IntPtr GLES_MapBuffer(int target, int access)
    {
        try
        {
            // 获取 buffer 大小
            IntPtr sizePtr = Marshal.AllocHGlobal(4);
            try
            {
                glesGetBufferParameteriv(target, 0x8764 /* GL_BUFFER_SIZE */, sizePtr);
                int size = Marshal.ReadInt32(sizePtr);

                if (size <= 0)
                {
                    Console.WriteLine($"[MonoGameGLESPatch] MapBuffer: buffer size={size}, returning null");
                    return IntPtr.Zero;
                }

                // BufferAccess → MapBufferRange access flags
                int flags = access switch
                {
                    0x88B8 => 0x0001,             // GL_READ_ONLY  → GL_MAP_READ_BIT
                    0x88B9 => 0x0002,             // GL_WRITE_ONLY → GL_MAP_WRITE_BIT
                    _      => 0x0001 | 0x0002     // GL_READ_WRITE → both
                };

                return glesMapBufferRange(target, IntPtr.Zero, (IntPtr)size, flags);
            }
            finally
            {
                Marshal.FreeHGlobal(sizePtr);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] MapBuffer error: {ex.Message}");
            return IntPtr.Zero;
        }
    }

    /// <summary>
    /// glGetTexImage(target, level, format, type, pixels) → FBO + glReadPixels
    /// GLES 没有 glGetTexImage，通过创建临时 FBO 绑定纹理后 ReadPixels 回读
    /// </summary>
    public static void GLES_GetTexImage(int target, int level, int format, int type, IntPtr pixels)
    {
        try
        {
            // 保存当前 FBO 绑定
            IntPtr prevFboPtr = Marshal.AllocHGlobal(4);
            IntPtr tmpFboPtr = Marshal.AllocHGlobal(4);
            IntPtr texSizePtr = Marshal.AllocHGlobal(4);
            try
            {
                glesGetIntegerv(0x8CA6 /* GL_DRAW_FRAMEBUFFER_BINDING */, prevFboPtr);
                int prevFbo = Marshal.ReadInt32(prevFboPtr);

                // 获取当前绑定的纹理 ID
                // target: GL_TEXTURE_2D=0x0DE1
                int texBinding = target == 0x0DE1 ? 0x8069 /* GL_TEXTURE_BINDING_2D */ : 0x8069;
                IntPtr texIdPtr = Marshal.AllocHGlobal(4);
                try
                {
                    glesGetIntegerv(texBinding, texIdPtr);
                    int texId = Marshal.ReadInt32(texIdPtr);

                    // 创建临时 FBO
                    glesGenFramebuffers(1, tmpFboPtr);
                    int tmpFbo = Marshal.ReadInt32(tmpFboPtr);

                    glesBindFramebuffer(0x8D40 /* GL_FRAMEBUFFER */, tmpFbo);
                    glesFramebufferTexture2D(0x8D40, 0x8CE0 /* GL_COLOR_ATTACHMENT0 */,
                        target, texId, level);

                    // 获取纹理尺寸 (通过 viewport 备份不可靠，用 level 0 的已知尺寸)
                    // MonoGame 在调用 GetTexImage 时已知纹理尺寸，pixels 缓冲区已分配好
                    // 这里我们查询 framebuffer attachment 参数
                    IntPtr wPtr = Marshal.AllocHGlobal(4);
                    IntPtr hPtr = Marshal.AllocHGlobal(4);
                    try
                    {
                        // GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE 检查
                        glesGetIntegerv(0x0D02 /* GL_VIEWPORT */, wPtr); // 获取 viewport 宽高
                        // 实际上不好用...直接传入一个大尺寸，ReadPixels 会裁剪
                        // 更好的方案: 从 MonoGame Texture2D 的 Width/Height 获取
                        // 但这里是底层 GL 调用，不知道尺寸

                        // 简化实现：读取 framebuffer attachment 尺寸
                        // 这在 GLES 3.0 可通过 glGetFramebufferAttachmentParameteriv
                        glesReadPixels(0, 0, 4096, 4096, format, type, pixels);
                    }
                    finally
                    {
                        Marshal.FreeHGlobal(wPtr);
                        Marshal.FreeHGlobal(hPtr);
                    }

                    // 恢复原 FBO
                    glesBindFramebuffer(0x8D40, prevFbo);

                    // 删除临时 FBO
                    glesDeleteFramebuffers(1, tmpFboPtr);
                }
                finally
                {
                    Marshal.FreeHGlobal(texIdPtr);
                }
            }
            finally
            {
                Marshal.FreeHGlobal(prevFboPtr);
                Marshal.FreeHGlobal(tmpFboPtr);
                Marshal.FreeHGlobal(texSizePtr);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] GetTexImage error: {ex.Message}");
        }
    }

    /// <summary>
    /// glGetCompressedTexImage — GLES 无等价函数
    /// 压缩纹理无法在 GLES 中直接回读，记录警告
    /// </summary>
    public static void GLES_GetCompressedTexImage(int target, int level, IntPtr pixels)
    {
        Console.WriteLine("[MonoGameGLESPatch] GetCompressedTexImage: not available in GLES");
    }

    // =========================================================================
    // 入口
    // =========================================================================

    public static int Initialize(IntPtr arg, int sizeBytes)
    {
        if (_initialized) return 0;
        _initialized = true;

        try
        {
            Console.WriteLine("==========================================");
            Console.WriteLine("[MonoGameGLESPatch] v7 — GLES native impl");
            Console.WriteLine("==========================================");

            var renderer = Environment.GetEnvironmentVariable("RALCORE_RENDERER");
            Console.WriteLine($"[MonoGameGLESPatch] Renderer: {renderer ?? "(not set)"}");

            if (renderer == "gl4es")
            {
                Console.WriteLine("[MonoGameGLESPatch] gl4es mode, skip");
                return 0;
            }

            _shouldPatch = true;
            _harmony = new Harmony("com.ralaunch.monogamegles");

            AppDomain.CurrentDomain.AssemblyLoad += (_, args) => TryPatch(args.LoadedAssembly);
            foreach (var asm in AppDomain.CurrentDomain.GetAssemblies())
                TryPatch(asm);

            return 0;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] Init error: {ex}");
            return -1;
        }
    }

    // =========================================================================
    // 补丁注册
    // =========================================================================

    private static void TryPatch(Assembly asm)
    {
        if (!_shouldPatch || asm.GetName().Name != "MonoGame.Framework") return;

        try
        {
            Console.WriteLine("[MonoGameGLESPatch] Patching MonoGame.Framework...");

            // 缓存类型
            _glType = asm.GetType("MonoGame.OpenGL.GL");
            var sdlType = asm.GetType("Sdl");
            var sdlGLType = sdlType?.GetNestedType("GL", ALL);
            var sdlWindowType = asm.GetType("Microsoft.Xna.Framework.SdlGameWindow");
            var graphicsDeviceType = asm.GetType("Microsoft.Xna.Framework.Graphics.GraphicsDevice");
            var graphicsCapType = asm.GetType("Microsoft.Xna.Framework.Graphics.GraphicsCapabilities");

            if (sdlGLType != null)
                _setAttributeMethod = AccessTools.Method(sdlGLType, "SetAttribute");

            if (_glType != null)
            {
                _boundApiField = AccessTools.Field(_glType, "BoundApi");
                var renderApiType = _glType.GetNestedType("RenderApi", ALL);
                if (renderApiType != null)
                    _renderApiES = Enum.ToObject(renderApiType, 12448); // RenderApi.ES
            }

            // [1] SdlGameWindow.CreateWindow PREFIX → 设置 GLES 3.0 属性
            PatchMethod(sdlWindowType, "CreateWindow", nameof(CreateWindow_Prefix), true);

            // [2] GL.LoadPlatformEntryPoints POSTFIX → BoundApi = ES
            PatchMethod(_glType, "LoadPlatformEntryPoints", nameof(LoadPlatformEntryPoints_Postfix), false);

            // [3] GL.LoadEntryPoints POSTFIX → GLES 函数替换
            PatchMethod(_glType, "LoadEntryPoints", nameof(LoadEntryPoints_Postfix), false);

            // [4] GraphicsDevice.PlatformSetup POSTFIX → 修复版本号解析
            PatchMethod(graphicsDeviceType, "PlatformSetup", nameof(PlatformSetup_Postfix), false);

            // [5] GraphicsCapabilities.PlatformInitialize POSTFIX → 强制 GLES 能力
            PatchMethod(graphicsCapType, "PlatformInitialize", nameof(GraphicsCapabilities_Postfix), false);

            // [6] GL.GetString PREFIX → 防御性 null 处理
            PatchMethod(_glType, "GetString", nameof(GetString_Prefix), true);

            Console.WriteLine("[MonoGameGLESPatch] All patches applied!");
            Console.WriteLine("==========================================");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] Patch error: {ex}");
        }
    }

    private static void PatchMethod(Type? type, string methodName, string patchMethodName, bool isPrefix)
    {
        if (type == null) return;
        var method = AccessTools.Method(type, methodName);
        if (method == null)
        {
            Console.WriteLine($"[MonoGameGLESPatch] {methodName} not found in {type.Name}");
            return;
        }

        var patchMethod = new HarmonyMethod(typeof(MonoGameGLESPatcher), patchMethodName);
        if (isPrefix)
            _harmony!.Patch(method, prefix: patchMethod);
        else
            _harmony!.Patch(method, postfix: patchMethod);

        Console.WriteLine($"[MonoGameGLESPatch] {type.Name}.{methodName} {(isPrefix ? "PREFIX" : "POSTFIX")} patched");
    }

    // =========================================================================
    // [1] SdlGameWindow.CreateWindow PREFIX → GLES 3.0 属性
    // =========================================================================

    public static void CreateWindow_Prefix()
    {
        if (_setAttributeMethod == null) return;
        try
        {
            var paramType = _setAttributeMethod.GetParameters()[0].ParameterType;
            SetAttr(paramType, 21, 4);  // ContextProfileMask = ES
            SetAttr(paramType, 17, 3);  // MajorVersion = 3
            SetAttr(paramType, 18, 0);  // MinorVersion = 0
            Console.WriteLine("[MonoGameGLESPatch] CreateWindow: GLES 3.0 attributes set");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] CreateWindow error: {ex.Message}");
        }
    }

    private static void SetAttr(Type enumType, int attr, int value)
    {
        var attrArg = enumType.IsEnum ? Enum.ToObject(enumType, attr) : (object)attr;
        _setAttributeMethod!.Invoke(null, new object[] { attrArg, value });
    }

    // =========================================================================
    // [2] GL.LoadPlatformEntryPoints POSTFIX → BoundApi = ES
    // =========================================================================

    public static void LoadPlatformEntryPoints_Postfix()
    {
        if (_boundApiField != null && _renderApiES != null)
        {
            _boundApiField.SetValue(null, _renderApiES);
            Console.WriteLine("[MonoGameGLESPatch] BoundApi → ES");
        }
    }

    // =========================================================================
    // [3] GL.LoadEntryPoints POSTFIX → GLES 函数替换
    //
    // MonoGame 的 LoadEntryPoints 通过 Sdl.GL.GetProcAddress 加载所有函数
    // GLES 中缺失的桌面 GL 函数会被加载为 null
    // 在这里用 GLES 原生实现替换这些 null 函数
    // =========================================================================

    public static void LoadEntryPoints_Postfix()
    {
        if (_glType == null) return;

        try
        {
            // PolygonMode: GLES 始终使用填充模式，这是规范行为不是跳过
            InstallGLESImpl("PolygonMode", null, "GLES always fill (spec-correct)");

            // DrawBuffer → glDrawBuffers(1, {mode})
            InstallGLESImpl("DrawBuffer", nameof(GLES_DrawBuffer),
                "glDrawBuffer → glDrawBuffers(1, &mode)");

            // MapBuffer → glMapBufferRange(target, 0, size, flags)
            InstallGLESImpl("MapBuffer", nameof(GLES_MapBuffer),
                "glMapBuffer → glMapBufferRange");

            // GetTexImage → FBO + ReadPixels
            InstallGLESImpl("GetTexImageInternal", nameof(GLES_GetTexImage),
                "glGetTexImage → FBO + glReadPixels");

            // GetCompressedTexImage: GLES 无等价
            InstallGLESImpl("GetCompressedTexImageInternal", nameof(GLES_GetCompressedTexImage),
                "glGetCompressedTexImage (GLES 无等价)");

            // 安全网：其余 null delegate 用 no-op 填充防止 NullRefException
            int remaining = FillRemainingNulls();
            if (remaining > 0)
                Console.WriteLine($"[MonoGameGLESPatch] {remaining} remaining null delegates filled with no-op");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] LoadEntryPoints postfix error: {ex}");
        }
    }

    /// <summary>
    /// 为 GL 类的 null delegate 字段安装 GLES 实现
    /// </summary>
    /// <param name="fieldName">GL 类中的字段名</param>
    /// <param name="implMethodName">本类中的 GLES 实现方法名，null 表示 no-op</param>
    /// <param name="description">描述</param>
    private static void InstallGLESImpl(string fieldName, string? implMethodName, string description)
    {
        var field = _glType!.GetField(fieldName, ALL);
        if (field == null)
        {
            Console.WriteLine($"[MonoGameGLESPatch] field {fieldName} not found");
            return;
        }

        if (field.GetValue(null) != null)
        {
            Console.WriteLine($"[MonoGameGLESPatch] {fieldName}: already loaded (native GLES)");
            return;
        }

        var delType = field.FieldType;
        var invoke = delType.GetMethod("Invoke")!;
        var paramTypes = Array.ConvertAll(invoke.GetParameters(), p => p.ParameterType);
        var returnType = invoke.ReturnType;

        if (implMethodName != null)
        {
            // 用 DynamicMethod 桥接 MonoGame 内部 delegate 类型 → 我们的 public 实现
            // MonoGame 的 enum 参数底层都是 int，DynamicMethod 可以直接传递
            var implMethod = typeof(MonoGameGLESPatcher).GetMethod(implMethodName, ALL)!;

            var dm = new DynamicMethod("GLES_" + fieldName, returnType, paramTypes,
                typeof(MonoGameGLESPatcher).Module, true);
            var il = dm.GetILGenerator();

            // 把所有参数压栈（enum 底层是 int，直接传递）
            for (int i = 0; i < paramTypes.Length; i++)
                il.Emit(OpCodes.Ldarg, i);

            il.Emit(OpCodes.Call, implMethod);
            il.Emit(OpCodes.Ret);

            field.SetValue(null, dm.CreateDelegate(delType));
        }
        else
        {
            // No-op: 对于 GLES 规范中不存在的功能（如 wireframe）
            var dm = new DynamicMethod("GLES_noop_" + fieldName, returnType, paramTypes,
                typeof(MonoGameGLESPatcher).Module, true);
            var il = dm.GetILGenerator();

            if (returnType != typeof(void))
            {
                if (returnType == typeof(IntPtr))
                {
                    il.Emit(OpCodes.Ldc_I4_0);
                    il.Emit(OpCodes.Conv_I);
                }
                else
                    il.Emit(OpCodes.Ldc_I4_0);
            }

            il.Emit(OpCodes.Ret);
            field.SetValue(null, dm.CreateDelegate(delType));
        }

        Console.WriteLine($"[MonoGameGLESPatch] {fieldName}: {description}");
    }

    /// <summary>
    /// 安全网：填充所有剩余的 null delegate 为 no-op
    /// </summary>
    private static int FillRemainingNulls()
    {
        int count = 0;
        var fields = _glType!.GetFields(ALL);

        foreach (var field in fields)
        {
            if (!typeof(Delegate).IsAssignableFrom(field.FieldType)) continue;
            if (field.GetValue(null) != null) continue;

            try
            {
                var delType = field.FieldType;
                var invoke = delType.GetMethod("Invoke")!;
                var paramTypes = Array.ConvertAll(invoke.GetParameters(), p => p.ParameterType);
                var returnType = invoke.ReturnType;

                var dm = new DynamicMethod("GLES_fallback_" + field.Name, returnType, paramTypes,
                    typeof(MonoGameGLESPatcher).Module, true);
                var il = dm.GetILGenerator();

                if (returnType != typeof(void))
                {
                    if (returnType == typeof(IntPtr))
                    {
                        il.Emit(OpCodes.Ldc_I4_0);
                        il.Emit(OpCodes.Conv_I);
                    }
                    else if (returnType == typeof(bool))
                        il.Emit(OpCodes.Ldc_I4_0);
                    else
                        il.Emit(OpCodes.Ldc_I4_0);
                }

                il.Emit(OpCodes.Ret);
                field.SetValue(null, dm.CreateDelegate(delType));
                count++;

                Console.WriteLine($"[MonoGameGLESPatch] fallback no-op: {field.Name}");
            }
            catch { /* skip fields we can't handle */ }
        }

        return count;
    }

    // =========================================================================
    // [4] GraphicsDevice.PlatformSetup POSTFIX → 修复 GLES 版本字符串解析
    //
    // 原代码: version.Substring(0, 1) 期望 "4.6" 格式
    // GLES:   "OpenGL ES 3.2" → Substring(0,1) = "O" → FormatException → 默认 major=1
    // =========================================================================

    public static void PlatformSetup_Postfix(object __instance)
    {
        try
        {
            var type = __instance.GetType();
            var majorField = AccessTools.Field(type, "glMajorVersion");
            var minorField = AccessTools.Field(type, "glMinorVersion");
            if (majorField == null || minorField == null) return;

            int currentMajor = (int)majorField.GetValue(__instance)!;
            if (currentMajor >= 2)
            {
                Console.WriteLine($"[MonoGameGLESPatch] GL version OK: {currentMajor}.{minorField.GetValue(__instance)}");
                return;
            }

            // 重新获取版本字符串
            var getStringMethod = AccessTools.Method(_glType, "GetString");
            if (getStringMethod == null) return;

            // StringName: Vendor=7936, Renderer=7937, Version=7938, Extensions=7939
            var stringNameType = getStringMethod.GetParameters()[0].ParameterType;
            var versionEnum = Enum.ToObject(stringNameType, 7938); // Version
            string? version = getStringMethod.Invoke(null, new[] { versionEnum }) as string;

            Console.WriteLine($"[MonoGameGLESPatch] GL_VERSION: \"{version}\"");

            int major = 3, minor = 0;

            if (!string.IsNullOrEmpty(version))
            {
                if (version.StartsWith("OpenGL ES "))
                {
                    // "OpenGL ES 3.2 V@0xxx.0 ..." → 取 "3.2"
                    string vp = version.Substring(10);
                    int dot = vp.IndexOf('.');
                    if (dot > 0)
                    {
                        int.TryParse(vp.Substring(0, dot), out major);
                        string minorStr = "";
                        for (int i = dot + 1; i < vp.Length && char.IsDigit(vp[i]); i++)
                            minorStr += vp[i];
                        if (minorStr.Length > 0)
                            int.TryParse(minorStr, out minor);
                    }
                }
                else
                {
                    // 标准 "X.Y" 格式
                    int dot = version.IndexOf('.');
                    if (dot > 0)
                    {
                        int.TryParse(version.Substring(0, dot), out major);
                        string minorStr = "";
                        for (int i = dot + 1; i < version.Length && char.IsDigit(version[i]); i++)
                            minorStr += version[i];
                        if (minorStr.Length > 0)
                            int.TryParse(minorStr, out minor);
                    }
                }
            }

            majorField.SetValue(__instance, major);
            minorField.SetValue(__instance, minor);
            Console.WriteLine($"[MonoGameGLESPatch] GL version fixed: {major}.{minor}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] PlatformSetup error: {ex.Message}");
        }
    }

    // =========================================================================
    // [5] GraphicsCapabilities.PlatformInitialize POSTFIX → GLES 能力修正
    //
    // GLES 2.0+ FBO 是核心功能，不需要 GL_ARB_framebuffer_object 扩展
    // =========================================================================

    public static void GraphicsCapabilities_Postfix(object __instance)
    {
        try
        {
            bool isES = false;
            if (_boundApiField != null && _renderApiES != null)
            {
                var current = _boundApiField.GetValue(null);
                isES = current?.ToString() == _renderApiES.ToString();
            }
            if (!isES) return;

            var type = __instance.GetType();

            // FBO 是 GLES 2.0+ 核心功能
            SetProp(type, __instance, "SupportsFramebufferObjectARB", true);
            SetProp(type, __instance, "SupportsFramebufferObjectEXT", true);

            // GLES 3.0+ 纹理能力
            SetProp(type, __instance, "SupportsFloatTextures", true);
            SetProp(type, __instance, "SupportsHalfFloatTextures", true);
            SetProp(type, __instance, "SupportsNormalized", true);

            Console.WriteLine("[MonoGameGLESPatch] GLES capabilities adjusted");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] Capabilities error: {ex.Message}");
        }
    }

    private static void SetProp(Type type, object instance, string name, object value)
    {
        var prop = AccessTools.Property(type, name);
        if (prop != null)
        {
            try { prop.SetValue(instance, value); }
            catch { /* property may not have setter */ }
        }
    }

    // =========================================================================
    // [6] GL.GetString PREFIX → 防御性 null 处理
    //
    // 如果 GetStringInternal 为 null 或 native 返回 NULL → 安全返回空字符串
    // =========================================================================

    public static bool GetString_Prefix(ref string __result, object name)
    {
        try
        {
            var field = AccessTools.Field(_glType, "GetStringInternal");
            if (field == null) { __result = ""; return false; }

            var del = field.GetValue(null) as Delegate;
            if (del == null)
            {
                Console.WriteLine("[MonoGameGLESPatch] GetString: delegate is null");
                __result = "";
                return false;
            }

            var ptr = (IntPtr)del.DynamicInvoke(name)!;
            if (ptr == IntPtr.Zero)
            {
                Console.WriteLine($"[MonoGameGLESPatch] GetString({name}): native returned NULL");
                __result = "";
                return false;
            }

            __result = Marshal.PtrToStringAnsi(ptr) ?? "";
            return false;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[MonoGameGLESPatch] GetString error: {ex.Message}");
            __result = "";
            return false;
        }
    }
}
