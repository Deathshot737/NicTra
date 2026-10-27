package com.nictra.data.repository

import com.nictra.data.local.SyncPreferences
import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

class TramiteRepository(
    private val tramiteDao: TramiteDao,
    private val syncPreferences: SyncPreferences
) {
    val lastSyncDate: Flow<String?> = syncPreferences.ultimaActualizacion

    fun getAllCategorias(): Flow<List<CategoriaEntity>> = tramiteDao.getAllCategorias()

    fun getTramitesByCategoria(categoriaId: String): Flow<List<TramiteEntity>> = 
        tramiteDao.getTramitesByCategoria(categoriaId)

    fun searchTramites(query: String): Flow<List<TramiteEntity>> {
        val normalizedQuery = query.normalizeForSearch()
        return tramiteDao.searchTramites(normalizedQuery)
    }

    suspend fun getTramiteById(tramiteId: String): TramiteEntity? = 
        tramiteDao.getTramiteById(tramiteId)

    fun getTramiteByIdFlow(tramiteId: String): Flow<TramiteEntity?> =
        tramiteDao.getTramiteByIdFlow(tramiteId)

    fun getRequisitosByTramite(tramiteId: String): Flow<List<RequisitoEntity>> = 
        tramiteDao.getRequisitosByTramite(tramiteId)

    fun getPasosByTramite(tramiteId: String): Flow<List<PasoEntity>> = 
        tramiteDao.getPasosByTramite(tramiteId)

    fun getDelegacionesByTramite(tramiteId: String): Flow<List<DelegacionEntity>> = 
        tramiteDao.getDelegacionesByTramite(tramiteId)

    fun getAllDelegaciones(): Flow<List<DelegacionEntity>> = 
        tramiteDao.getAllDelegaciones()

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

    private fun String.normalizeForSearch(): String {
        val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").lowercase()
    }
}
