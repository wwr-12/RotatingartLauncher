package com.app.ralaunch.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 导航状态
 */
@Stable
class NavState(
    initialScreen: Screen = Screen.Games
) {
    /** 当前屏幕 */
    var currentScreen by mutableStateOf(initialScreen)
        private set

    /** 导航方向 (用于切换动画) */
    var isForwardNavigation by mutableStateOf(true)
        private set

    /** 返回栈 */
    private val backStack = mutableListOf<Screen>()

    /** 是否可以返回 */
    val canGoBack: Boolean get() = backStack.isNotEmpty()

    /** 当前主导航目的地 */
    val currentDestination: NavDestination?
        get() = NavDestination.fromScreen(currentScreen)
            ?: NavDestination.fromRoute(currentScreen.route.split("/").first())

    /**
     * 导航到指定屏幕
     * @param screen 目标屏幕
     * @param addToBackStack 是否添加到返回栈
     */
    fun navigateTo(screen: Screen, addToBackStack: Boolean = true) {
        if (addToBackStack && currentScreen != screen) {
            backStack.add(currentScreen)
        }
        isForwardNavigation = true
        currentScreen = screen
    }

    /**
     * 导航到主导航目的地
     * 主导航之间切换时清空返回栈
     */
    fun navigateTo(destination: NavDestination) {
        if (currentDestination != destination) {
            val oldDest = currentDestination
            val newDest = destination
            
            // 计算导航方向
            if (oldDest != null) {
                isForwardNavigation = newDest.ordinal > oldDest.ordinal
            }
            
            backStack.clear()
            currentScreen = destination.screen
        }
    }

    /**
     * 返回上一个屏幕
     * @return 是否成功返回
     */
    fun goBack(): Boolean {
        return if (backStack.isNotEmpty()) {
            isForwardNavigation = false
            currentScreen = backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }

    /**
     * 返回到主页面
     */
    fun popToRoot() {
        isForwardNavigation = false
        backStack.clear()
        currentScreen = Screen.Games
    }

    /**
     * 重置到指定屏幕
     */
    fun resetTo(screen: Screen) {
        isForwardNavigation = true
        backStack.clear()
        currentScreen = screen
    }
}

/**
 * 创建并记住 NavState
 */
@Composable
fun rememberNavState(
    initialScreen: Screen = Screen.Games
): NavState {
    return remember { NavState(initialScreen) }
}
