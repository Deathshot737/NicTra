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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelegacionTramiteCrossRefs(crossRefs: List<DelegacionTramiteCrossRef>)

    @Query("DELETE FROM categorias WHERE id NOT IN (:ids)")
    suspend fun deleteCategoriasExcept(ids: List<String>)

    @Query("DELETE FROM tramites WHERE id NOT IN (:ids)")
    suspend fun deleteTramitesExcept(ids: List<String>)

    @Query("DELETE FROM requisitos WHERE id NOT IN (:ids)")
    suspend fun deleteRequisitosExcept(ids: List<String>)

    @Query("DELETE FROM pasos WHERE id NOT IN (:ids)")
    suspend fun deletePasosExcept(ids: List<String>)

    @Query("DELETE FROM delegaciones WHERE id NOT IN (:ids)")
    suspend fun deleteDelegacionesExcept(ids: List<String>)

    @Query("DELETE FROM delegacion_tramites")
    suspend fun deleteAllDelegacionTramites()

    @Query("SELECT * FROM tramites")
    suspend fun getAllTramitesList(): List<TramiteEntity>

    @Query("SELECT * FROM requisitos")
    suspend fun getAllRequisitosList(): List<RequisitoEntity>

    @Transaction
    suspend fun applySnapshot(
        categorias: List<CategoriaEntity>,
        tramites: List<TramiteEntity>,
        requisitos: List<RequisitoEntity>,
        pasos: List<PasoEntity>,
        delegaciones: List<DelegacionEntity>,
        crossRefs: List<DelegacionTramiteCrossRef>
    ) {
        // 1. UPSERT (INSERT OR REPLACE)
        insertCategorias(categorias)
        insertTramites(tramites)
        insertRequisitos(requisitos)
        insertPasos(pasos)
        insertDelegaciones(delegaciones)
        
        deleteAllDelegacionTramites()
        insertDelegacionTramiteCrossRefs(crossRefs)

        // 2. RECONCILIACIÓN
        deletePasosExcept(pasos.map { it.id })
        deleteRequisitosExcept(requisitos.map { it.id })
        deleteTramitesExcept(tramites.map { it.id })
        deleteCategoriasExcept(categorias.map { it.id })
        deleteDelegacionesExcept(delegaciones.map { it.id })
    }

    @Query("SELECT * FROM categorias")
    fun getAllCategorias(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias LIMIT 1")
    suspend fun getAnyCategoria(): CategoriaEntity?

    @Query("SELECT * FROM tramites WHERE categoriaId = :categoriaId")
    fun getTramitesByCategoria(categoriaId: String): Flow<List<TramiteEntity>>

    @Query("""
        SELECT * FROM tramites 
        WHERE 
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(titulo, 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u')) LIKE '%' || :query || '%' 
            OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(entidad, 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u')) LIKE '%' || :query || '%' 
            OR LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(descripcion, 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u')) LIKE '%' || :query || '%'
    """)
    fun searchTramites(query: String): Flow<List<TramiteEntity>>

    @Query("SELECT * FROM tramites WHERE id = :tramiteId")
    suspend fun getTramiteById(tramiteId: String): TramiteEntity?

    @Query("SELECT * FROM tramites WHERE id = :tramiteId")
    fun getTramiteByIdFlow(tramiteId: String): Flow<TramiteEntity?>

    @Query("SELECT * FROM requisitos WHERE tramiteId = :tramiteId")
    fun getRequisitosByTramite(tramiteId: String): Flow<List<RequisitoEntity>>

    @Query("SELECT * FROM pasos WHERE tramiteId = :tramiteId ORDER BY orden ASC")
    fun getPasosByTramite(tramiteId: String): Flow<List<PasoEntity>>

    @Transaction
    @Query("""
        SELECT d.* FROM delegaciones d
        INNER JOIN delegacion_tramites dt ON d.id = dt.delegacionId
        WHERE dt.tramiteId = :tramiteId
    """)
    fun getDelegacionesByTramite(tramiteId: String): Flow<List<DelegacionEntity>>

    @Query("SELECT * FROM delegaciones")
    fun getAllDelegaciones(): Flow<List<DelegacionEntity>>

    @Query("SELECT * FROM tramites WHERE isQuickAccess = 1")
    fun getQuickAccessTramites(): Flow<List<TramiteEntity>>

    @Query("SELECT * FROM tramites WHERE lastVisited IS NOT NULL ORDER BY lastVisited DESC LIMIT 5")
    fun getRecentTramites(): Flow<List<TramiteEntity>>

    @Query("SELECT COUNT(*) FROM tramites WHERE isCompleted = 1")
    fun getCompletedTramitesCount(): Flow<Int>

    @Query("""
        SELECT t.*, 
        (SELECT COUNT(*) FROM requisitos r WHERE r.tramiteId = t.id) as totalRequisitos,
        (SELECT COUNT(*) FROM requisitos r WHERE r.tramiteId = t.id AND r.isChecked = 1) as checkedRequisitos
        FROM tramites t
        WHERE t.lastVisited IS NOT NULL OR (SELECT COUNT(*) FROM requisitos r WHERE r.tramiteId = t.id AND r.isChecked = 1) > 0
    """)
    fun getMyTramitesWithProgress(): Flow<List<TramiteProgress>>

    @Query("UPDATE tramites SET lastVisited = :timestamp WHERE id = :tramiteId")
    suspend fun updateLastVisited(tramiteId: String, timestamp: Long?)

    @Query("UPDATE tramites SET isCompleted = :completed WHERE id = :tramiteId")
    suspend fun updateCompletionStatus(tramiteId: String, completed: Boolean)

    @Query("UPDATE requisitos SET isChecked = :checked WHERE id = :requisitoId")
    suspend fun updateRequisitoStatus(requisitoId: String, checked: Boolean)

    @Transaction
    suspend fun resetTramite(tramiteId: String) {
        updateLastVisited(tramiteId, null)
        updateCompletionStatus(tramiteId, false)
        uncheckAllRequisitos(tramiteId)
    }

    @Query("UPDATE requisitos SET isChecked = 0 WHERE tramiteId = :tramiteId")
    suspend fun uncheckAllRequisitos(tramiteId: String)
}
