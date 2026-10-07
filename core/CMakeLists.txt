cmake_minimum_required(VERSION 3.22.1)
project("main")

# =========================
# 基础配置
# =========================
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# =========================
# libs 根目录
# =========================
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/libs)

include(cmake/Gl4esOptions.cmake)
include(cmake/SdlOptions.cmake)
include(cmake/FaudioOptions.cmake)
include(cmake/Fna3dOptions.cmake)

# SDL3（可选）：vanilla Terraria 的 FNA 引用 libSDL3（FNA.dll.config: SDL3→libSDL3.so.0），
# 而启动器仅内置 libSDL2.so。vendoring SDL3 源后自动启用（需联网）：
#   git submodule add https://gh-proxy.com/https://github.com/libsdl-org/SDL.git core/libs/SDL3
#   # 并在 core/libs/SDL3 切到 release-3.2.x tag（如 release-3.2.30）
# 未 vendoring 时此块被跳过，不影响现有构建。
if(EXISTS ${LIBS_DIR}/SDL3/CMakeLists.txt)
    include(cmake/Sdl3Options.cmake)
    set(RAL_SDL3_FOUND ON)
endif()

add_subdirectory(${LIBS_DIR}/dotnethost)
add_subdirectory(${LIBS_DIR}/FMOD_SDL_BUILD_DEPS)
add_subdirectory(${LIBS_DIR}/dobby)
add_subdirectory(${LIBS_DIR}/spdlog-1.17.0)

# =========================
# 主程序（保持独立）
# =========================

add_library(${CMAKE_PROJECT_NAME} SHARED
        src/logger.cpp
        src/jni_entry.cpp
        src/osm_renderer.cpp
        src/dotnet/corehost_hooks.cpp
        src/dotnet/dotnet_launcher.cpp
        src/sdl/sdl_entry.cpp
        src/sdl/sdl_input_bridge_extend.cpp
        src/native_stdio.cpp
        src/game_launcher.cpp
        src/shared_envvars.cpp
        src/thread_affinity_manager.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        SDL2
        FAudio-shared
        FNA3D
        mojoshader
        dotnethost
        dobby
        spdlog::spdlog
        ${CXX_SHARED_LIB}
)

# 包含头文件
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${SDL_SOURCE_DIR}/include
        ${FNA3D_SOURCE_DIR}/include
        ${DOTNETHOST_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/liblinkernsbypass
        ${CMAKE_CURRENT_SOURCE_DIR}
        includes
)

# =========================
# 安装
# =========================
install(TARGETS SDL2 FAudio-shared FNA3D dotnethost fmod_SDL
        DESTINATION lib)

install(TARGETS GL_gl4es EGL_gl4es
        DESTINATION lib)

# SDL3 不链接进 libmain.so：SDL 的 CMake 禁止同一目标同时链接 SDL2 与 SDL3
# （INTERFACE_SDL_VERSION 2/3 冲突）。仅 add_subdirectory 让 libSDL3.so 编出并由
# AGP 打包；vanilla FNA 的 [DllImport("SDL3")] 运行时经 LD_LIBRARY_PATH dlopen 命中。
if(RAL_SDL3_FOUND)
    install(TARGETS SDL3-shared DESTINATION lib)
endif()