package com.nictra.data.local.dao

import androidx.room.*
import com.nictra.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TramiteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorias(categorias: List<CategoriaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTramites(tramites: List<TramiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequisitos(requisitos: List<RequisitoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasos(pasos: List<PasoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelegaciones(delegaciones: List<DelegacionEntity>)

    @Query("SELECT * FROM categorias")
    fun getAllCategorias(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias LIMIT 1")
    suspend fun getAnyCategoria(): CategoriaEntity?

    @Query("SELECT * FROM tramites WHERE categoriaId = :categoriaId")
    fun getTramitesByCategoria(categoriaId: String): Flow<List<TramiteEntity>>

    @Query("SELECT * FROM tramites WHERE titulo LIKE '%' || :query || '%'")
    fun searchTramites(query: String): Flow<List<TramiteEntity>>

    @Query("SELECT * FROM tramites WHERE id = :tramiteId")
    suspend fun getTramiteById(tramiteId: String): TramiteEntity?

    @Query("SELECT * FROM requisitos WHERE tramiteId = :tramiteId")
    fun getRequisitosByTramite(tramiteId: String): Flow<List<RequisitoEntity>>

    @Query("SELECT * FROM pasos WHERE tramiteId = :tramiteId ORDER BY orden ASC")
    fun getPasosByTramite(tramiteId: String): Flow<List<PasoEntity>>

    @Query("SELECT * FROM delegaciones WHERE tramiteId = :tramiteId")
    fun getDelegacionesByTramite(tramiteId: String): Flow<List<DelegacionEntity>>

    @Query("SELECT * FROM tramites WHERE isQuickAccess = 1")
    fun getQuickAccessTramites(): Flow<List<TramiteEntity>>

    @Query("SELECT * FROM tramites WHERE lastVisited IS NOT NULL ORDER BY lastVisited DESC LIMIT 5")
    fun getRecentTramites(): Flow<List<TramiteEntity>>

    @Query("SELECT COUNT(*) FROM tramites WHERE isCompleted = 1")
    fun getCompletedTramitesCount(): Flow<Int>

    @Query("UPDATE tramites SET lastVisited = :timestamp WHERE id = :tramiteId")
    suspend fun updateLastVisited(tramiteId: String, timestamp: Long)

    @Query("UPDATE tramites SET isCompleted = :completed WHERE id = :tramiteId")
    suspend fun updateCompletionStatus(tramiteId: String, completed: Boolean)

    @Query("UPDATE requisitos SET isChecked = :checked WHERE id = :requisitoId")
    suspend fun updateRequisitoStatus(requisitoId: String, checked: Boolean)
}
