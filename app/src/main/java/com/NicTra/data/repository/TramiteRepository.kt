package com.nictra.data.repository

import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class TramiteRepository(
    private val tramiteDao: TramiteDao
) {
    fun getAllCategorias(): Flow<List<CategoriaEntity>> = tramiteDao.getAllCategorias()

    fun getTramitesByCategoria(categoriaId: String): Flow<List<TramiteEntity>> = 
        tramiteDao.getTramitesByCategoria(categoriaId)

    fun searchTramites(query: String): Flow<List<TramiteEntity>> = 
        tramiteDao.searchTramites(query)

    suspend fun getTramiteById(tramiteId: String): TramiteEntity? = 
        tramiteDao.getTramiteById(tramiteId)

    fun getRequisitosByTramite(tramiteId: String): Flow<List<RequisitoEntity>> = 
        tramiteDao.getRequisitosByTramite(tramiteId)

    fun getPasosByTramite(tramiteId: String): Flow<List<PasoEntity>> = 
        tramiteDao.getPasosByTramite(tramiteId)

    fun getDelegacionesByTramite(tramiteId: String): Flow<List<DelegacionEntity>> = 
        tramiteDao.getDelegacionesByTramite(tramiteId)

    fun getQuickAccessTramites(): Flow<List<TramiteEntity>> = tramiteDao.getQuickAccessTramites()

    fun getRecentTramites(): Flow<List<TramiteEntity>> = tramiteDao.getRecentTramites()
    
    fun getCompletedTramitesCount(): Flow<Int> = tramiteDao.getCompletedTramitesCount()

    fun getMyTramitesWithProgress(): Flow<List<TramiteProgress>> = 
        tramiteDao.getMyTramitesWithProgress()

    suspend fun markTramiteAsVisited(tramiteId: String) {
        tramiteDao.updateLastVisited(tramiteId, System.currentTimeMillis())
    }
    
    suspend fun updateCompletionStatus(tramiteId: String, completed: Boolean) {
        tramiteDao.updateCompletionStatus(tramiteId, completed)
    }

    suspend fun updateRequisitoStatus(requisitoId: String, checked: Boolean) {
        tramiteDao.updateRequisitoStatus(requisitoId, checked)
    }

    suspend fun resetTramite(tramiteId: String) {
        tramiteDao.resetTramite(tramiteId)
    }
}
