# TerrariaCompatPatch — 原版 Terraria on CoreCLR（方案 A）

让 GOG Linux 版原版 `Terraria.exe`（.NET Framework 4.0 + FNA IL）在启动器 .NET 10 CoreCLR 上运行。

## 原理

- `.sh` 安装本就产生 `GameDefinition.TERRARIA`（`launchTarget=Terraria.exe`）条目，`Terraria.exe` 解包后存在 —— **装/识别侧已就绪，本补丁解决启动期兼容**。
- `GameLauncher`（Kotlin 侧）：
  - `ensureRuntimeConfig`：为无 runtimeconfig 的 .NET Fx 程序集生成 framework-dependent 配置；
  - `quarantineLegacyBclFacades`：隔离游戏包自带的 Mono 门面 DLL（`_ral_legacy_bcl/`），防止劫持 CoreCLR 探测；
- `StartupHook`（`DOTNET_STARTUP_HOOKS`）：
  - `System.Windows.Forms` → **随补丁发布的真实桩程序集**（真元数据，JIT 最友好）；缺失时动态 Emit 兜底；
  - 其他依赖 → 照常从 `MONOMOD_PATH` / 补丁目录加载（与 ConsolePatch 相同）
- `Patcher`：MetadataReader 扫描 Terraria.exe 引用清单（日志）；Harmony（`com.ralaunch.terraria.compat`）做弱类型打桩。
- Console API 由 ConsolePatch（`targetGames=["*"]`）覆盖，本补丁不重复。

## 构建与打包

```bash
scripts/build_terraria_compat_patch.sh
./gradlew assembleDebug
```

脚本构建 `TerrariaCompatPatch` + `WinFormsStub` 两个项目，并把
`TerrariaCompatPatch.dll` + `patch.json` + `System.Windows.Forms.dll` 打成
`app/src/main/assets/patches/com.app.ralaunch.terraria.compat.zip`（扁平结构，与现有补丁一致）。

## ⚠️ 依赖：arm64 libSDL3.so

vanilla 的 `FNA.dll.config` 主映射 `SDL3→libSDL3.so.0`，而启动器仅内置 `libSDL2.so`；
CoreCLR 不认 dllmap，`[DllImport("SDL3")]` 会 dlopen 失败。CMake 侧已就绪（guarded）：

```bash
git submodule add https://github.com/libsdl-org/SDL.git core/libs/SDL3
git -C core/libs/SDL3 checkout release-3.2.30   # 或其他 release-3.2.x tag
./gradlew assembleDebug
```

vendoring 后 `core/CMakeLists.txt` 的 guarded 块自动启用，产出并打包 `libSDL3.so`。
注意 SDL 的 CMake **禁止同一目标同时链接 SDL2 与 SDL3**（INTERFACE_SDL_VERSION 冲突），
故 SDL3 只 add_subdirectory + install，不链入 libmain —— FNA 运行时经 `LD_LIBRARY_PATH` dlopen 命中。

## 迭代历史 / 已解决的卡点

1. runtimeconfig 缺失 → hostfxr 按 self-contained 启动失败 → `ensureRuntimeConfig` 修复；
2. 游戏包 Mono 门面劫持探测 → JIT SIGABRT → `quarantineLegacyBclFacades` 修复；
3. TypeResolve 惰性建类型撞 JIT → 改为发布真实桩程序集 + Main 前急切预建；
4. arm64 tiered JIT + W^X 崩溃 → `DotNetLauncher` 对 legacy 程序设
   `DOTNET_TieredCompilation=0` / `DOTNET_TC_QuickJit=0` / `DOTNET_EnableWriteXorExecute=0`。
5. PC 复现证明 .NET Fx IL → CoreCLR 本身可行（x64 上为干净的托管异常，非 SIGABRT）。

## 已知局限

- WinForms 桩成员为 no-op/固定值（对照 Terraria 的 22 个 MemberRef 实现）；行为异常按日志调。
- System.Data 等其他 legacy 引用走动态兜底；如需精确桩再按日志补。
