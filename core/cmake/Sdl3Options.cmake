# SDL3 选项（仿 SdlOptions.cmake）
# 供 vanilla Terraria 的 FNA 使用：FNA.dll.config 将 SDL3 映射到 libSDL3.so.0，
# 启动器仅内置 libSDL2.so，需补 arm64 libSDL3.so。
# 启用前提：已 vendoring SDL3 源到 core/libs/SDL3（见 core/CMakeLists.txt 的 guarded 块）。
set(SDL_VIDEO_OPENGL     ON CACHE BOOL "" FORCE)
set(SDL_VIDEO_OPENGL_ES ON CACHE BOOL "" FORCE)
set(SDL_VIDEO_OPENGL_EGL ON CACHE BOOL "" FORCE)
set(SDL_OPENGLES        ON CACHE BOOL "" FORCE)

add_compile_definitions(SDL_VIDEO_OPENGL_GL4ES)
add_subdirectory(${LIBS_DIR}/SDL3)
