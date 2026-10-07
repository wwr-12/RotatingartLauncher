# 上传前清理说明

> 本文件记录本次 GitHub 上传所做的清理，以及克隆本仓库时必须注意的事项。

## 一、为什么另建了一个目录

原项目 `RotatingartLauncher/` **未被修改**，仍在原位、可继续正常编译。

清理后的待上传仓库位于同级目录 `_RAL_upload/`，它是一个**全新的 Git 仓库**（单一初始提交），
与原项目完全隔离。这样做是为了避免破坏原项目里 6 个子模块的 gitlink 索引。

原仓库供参考，如需查看被删除内容：`C:\Users\Administrator\Desktop\arm64\RotatingartLauncher`（分支 `feat/vanilla-terraria-coreclr`）

## 二、清理了什么

| 项目 | 处理 | 原因 |
|---|---|---|
| `.mimosa/` | **删除 3982 个文件** | AI 助手的会话状态、hook 快照、baseline 副本。占原仓库跟踪文件的 80%，纯本地垃圾 |
| `.codex` | 删除 | 0 字节空文件 |
| `.agents/` | 删除 13 个文件 | AI 助手本地技能定义，不属于项目代码 |
| `hs_err_pid*.log` / `replay_pid*.log` | 未纳入 | 崩溃转储共约 7.5 MB，已由 `*.log` 规则排除 |
| `.gitmodules` 中 `gh-proxy.com` 代理 | 改为官方地址 | 代理地址会让 GitHub Actions 和海外 clone 失败 |
| `Theorafile` / `box64` / `dxvk` 子模块声明 | 删除 | **目录根本不存在**，`git clone --recursive` 会直接报错中断 |
| 子模块 key 名 | 修正 | 原 key 为 `app/src/main/cpp/xxx` 但 path 指向 `core/libs/xxx`，不一致 |
| `patches/TerrariaCompatPatch/README.md` | 修正 | 文档中残留 gh-proxy 代理地址 |

**未改动**：所有业务源码、资源、CI workflow、LICENSE、README、AGENTS.md。

## 三、安全确认

以下敏感文件**从未进入 Git 历史**，已核验：

- `key.jks`（签名密钥）
- `key.properties`（内含 `storePassword` / `keyPassword`）
- `local.properties`（SDK 路径）

`.gitignore` 中 `*.jks` / `key.properties` / `local.properties` 规则保持有效，已验证暂存区无泄漏。

> 如果你将来要把签名配置改为从环境变量或 CI Secrets 读取，需要在
> `app/build.gradle.kts` 中确认读取逻辑（当前依赖本地 `key.properties`）。

## 四、克隆本仓库（重要）

本仓库包含 **6 个子模块**和 **31 个 Git LFS 文件**，克隆必须带参数：

```bash
git clone --recurse-submodules https://github.com/<你的账号>/<你的仓库>.git
```

如果已经克隆过了，补齐子模块：

```bash
git submodule update --init --recursive
```

### 子模块清单

| 路径 | 来源 |
|---|---|
| `core/libs/FNA3D` | RotatingArtDev/FNA3D |
| `core/libs/FAudio` | RotatingArtDev/FAudio |
| `core/libs/gl4es` | RotatingArtDev/gl4es |
| `core/libs/FMOD_SDL` | RotatingArtDev/FMOD_SDL |
| `core/libs/SDL` | RotatingArtDev/SDL |
| `core/libs/SDL3` | libsdl-org/SDL |

### 国内网络环境

子模块地址已移除 `gh-proxy.com` 代理。**如果你在中国大陆且克隆子模块超时**，可临时使用：

```bash
git config --global url."https://gh-proxy.com/https://github.com/".insteadOf "https://github.com/"
```

这只影响你本机，不影响仓库内容。设置完记得用 `--unset` 撤销。

App 运行时给用户提供的「使用加速站 (gh-proxy.com)」选项是**功能代码**，属于国内下载加速，
已保留在 `ModLoaderVersionFetcher.kt` 和 `strings.xml` 中，与子模块代理无关。

## 五、Git LFS

`.gitattributes` 中已配置以下类型走 LFS：

```
*.zip    *.so    *.tar.xz
```

涉及 31 个文件，主要是：
- `app/src/main/jniLibs/arm64-v8a/` 下的 `.so` 原生库
- `app/src/main/assets/dotnet.tar.xz`（.NET 运行时，21 MB）
- `app/src/main/assets/patches/*.zip`（游戏补丁包）

GitHub **免费账户提供 10 GB LFS 存储/ 10 GB 带宽配额**，当前 LFS 总量约 86 MiB，在配额内。

> ⚠️ GitHub 不允许免费账户推送超过 100 MB 的**单个**文件。当前最大单文件为
> `dotnet.tar.xz`（21 MB），安全。

推送到新仓库时需确保已安装 Git LFS（`git lfs install`），否则大文件会作为普通文件入库，
导致仓库体积暴涨。

## 六、推送步骤

```bash
cd _RAL_upload

# 1. 在 GitHub 上先创建空仓库（不要勾选 README/.gitignore/LICENSE）
# 2. 关联并推送
git remote add origin https://github.com/<你的账号>/<你的仓库>.git
git push -u origin main
```

推送后 GitHub Actions 的 `Build RAL APK` workflow 可通过手动触发运行
（Actions → Build RAL APK (Debug & Safe) → Run workflow）。

## 七、CI 说明

仓库内 `.github/workflows/` 含两个 workflow，均为 `workflow_dispatch` 手动触发：

- `build-apk.yml` — 构建 Debug 与 Safe Release APK
- `build-debug-apk.yml` — 仅构建 Debug

两者均使用 `submodules: recursive` + `lfs: true` 检出，因此**上述子模块和 LFS 配置必须保持完整**。
