#!/usr/bin/env bash
# 构建 TerrariaCompatPatch（含 WinFormsStub 桩程序集）并打包进 APK assets
#
# 用法: scripts/build_terraria_compat_patch.sh [repo-root]
# 依赖: dotnet SDK (net8.0), python3
#
# 产物: app/src/main/assets/patches/com.app.ralaunch.terraria.compat.zip
#       （内含 TerrariaCompatPatch.dll + patch.json + System.Windows.Forms.dll，
#        运行时由 PatchManager.installBuiltInPatches 自动发现并安装）
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
PATCH_DIR="$ROOT/patches/TerrariaCompatPatch"
STUB_DIR="$ROOT/patches/WinFormsStub"
ASSETS="$ROOT/app/src/main/assets/patches"

echo "==> dotnet build (Release)"
cd "$PATCH_DIR"
dotnet build -c Release

STUB_DLL="$STUB_DIR/bin/Release/net8.0/System.Windows.Forms.dll"
if [ -d "$STUB_DIR" ]; then
    echo "==> dotnet build WinFormsStub (Release)"
    (cd "$STUB_DIR" && dotnet build -c Release)
fi

echo "==> package zip"
DLL="$PATCH_DIR/bin/Release/net8.0/TerrariaCompatPatch.dll"
PJ="$PATCH_DIR/patch.json"
OUT="$ASSETS/com.app.ralaunch.terraria.compat.zip"
python - "$DLL" "$PJ" "$STUB_DLL" "$OUT" <<'PY'
import sys, zipfile, os
dll, pj, stub_dll, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(dll, 'TerrariaCompatPatch.dll')
    z.write(pj, 'patch.json')
    if os.path.exists(stub_dll):
        z.write(stub_dll, 'System.Windows.Forms.dll')
print('packaged:', out, os.path.getsize(out), 'bytes')
PY

echo "==> done. 重新构建 APK 后补丁即随包分发。"
