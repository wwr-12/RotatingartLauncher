package com.app.ralaunch.core.di.contract

import com.app.ralaunch.core.model.GameItem
import kotlinx.coroutines.flow.StateFlow

/**
 * 游戏数据仓库 V3
 *
 * 统一负责游戏列表持久化、存储目录分配与游戏文件删除。
 */
interface IGameRepositoryServiceV3 {
    val games: StateFlow<List<GameItem>>

    suspend fun getById(id: String): GameItem?
    suspend fun upsert(game: GameItem, index: Int = 0)
    suspend fun removeById(id: String)
    suspend fun removeAt(index: Int)
    suspend fun reorder(from: Int, to: Int)
    suspend fun replaceAll(games: List<GameItem>)
    suspend fun clear()

    fun getGameGlobalStorageDirFull(): String
    fun createGameStorageRoot(gameId: String): Pair<String, String>
    fun deleteGameFiles(game: GameItem): Boolean
}
