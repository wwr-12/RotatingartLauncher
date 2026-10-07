package com.app.ralaunch.feature.main

import com.app.ralaunch.core.model.GameItem

/**
 * 主界面 MVP 契约
 */
interface MainContract {

    /**
     * View 接口 - MainActivity 实现
     */
    interface View {
        // 游戏列表
        fun showGameList(games: List<GameItem>)
        fun refreshGameList()
        
        // 游戏选择
        fun showSelectedGame(game: GameItem)
        fun showNoGameSelected()
        fun showLaunchButton()
        fun hideLaunchButton()
        
        // 导航
        fun showGamePage()
        fun showSettingsPage()
        fun showControlPage()
        fun showDownloadPage()
        fun showImportPage()
        
        // 状态
        fun showLoading()
        fun hideLoading()
        
        // 消息
        fun showToast(message: String)
        fun showError(message: String)
        fun showSuccess(message: String)
        
        // 启动游戏
        fun launchGame(game: GameItem)
    }

    /**
     * Presenter 接口
     */
    interface Presenter {
        fun attach(view: View)
        fun detach()
        
        // 生命周期
        fun onCreate()
        fun onResume()
        fun onPause()
        fun onDestroy()
        
        // 游戏操作
        fun loadGameList()
        fun selectGame(game: GameItem)
        fun deleteGame(game: GameItem, position: Int)
        fun launchSelectedGame()
        fun addGame(game: GameItem)
        
        // 导航
        fun onNavigationSelected(itemId: Int): Boolean
        fun onBackPressed(): Boolean
        
        // 导入
        fun onGameImportComplete(gameType: String, game: GameItem)
    }
}
