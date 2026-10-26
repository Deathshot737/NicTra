package com.nictra.data.repository

import android.util.Log
import com.nictra.data.local.SyncPreferences
import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import com.nictra.data.remote.ApiService
import com.nictra.data.remote.dto.FeedDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncRepository(
    private val apiService: ApiService,
    private val tramiteDao: TramiteDao,
    private val syncPreferences: SyncPreferences
) {
    private val syncMutex = Mutex()
    private val FORCED_SYNC_INTERVAL = 24 * 60 * 60 * 1000L // 24 horas en milisegundos

    suspend fun syncFeed(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                // 1. Obtener versión remota
                val remoteVersionInfo = apiService.getFeedVersion()
                val localVersion = syncPreferences.feedVersion.first()
                val lastFullSyncTime = syncPreferences.lastFullSyncTime.first()
                
                val isStale = System.currentTimeMillis() - lastFullSyncTime > FORCED_SYNC_INTERVAL
                val shouldForce = force || isStale

                // 2. Decidir si descargar
                if (!shouldForce && remoteVersionInfo.version == localVersion) {
                    Log.i("SyncRepo", "Feed ya está actualizado (versión: $localVersion)")
                    return@withContext Result.success(Unit)
                }

                // 3. Descargar snapshot completo
                Log.i("SyncRepo", "Descargando nuevo feed snapshot (Force: $shouldForce)...")
                val snapshot = apiService.getFeed()

                // 4. Aplicar snapshot preservando estado local
                applySnapshot(snapshot)

                // 5. Guardar nueva versión e info de sync
                syncPreferences.saveSyncInfo(
                    version = remoteVersionInfo.version, 
                    fecha = remoteVersionInfo.actualizadoEn,
                    fullSync = true
                )
                
                Log.i("SyncRepo", "Sincronización exitosa: ${remoteVersionInfo.version}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SyncRepo", "Error durante la sincronización", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun applySnapshot(dto: FeedDto) {
        // Validación: los 6 arrays deben estar presentes
        if (dto.categorias == null || dto.tramites == null || dto.requisitos == null || 
            dto.pasos == null || dto.delegaciones == null || dto.delegacionTramites == null) {
             throw IllegalArgumentException("Snapshot incompleto: faltan colecciones requeridas")
        }

        // Obtener datos actuales para preservar el estado del usuario
        val existingTramites = tramiteDao.getAllTramitesList().associateBy { it.id }
        val existingRequisitos = tramiteDao.getAllRequisitosList().associateBy { it.id }

        val categorias = dto.categorias.map { CategoriaEntity(it.id, it.nombre, it.icono) }
        
        val tramites = dto.tramites.map { dtoItem ->
            val existing = existingTramites[dtoItem.id]
            TramiteEntity(
                id = dtoItem.id,
                categoriaId = dtoItem.categoriaId,
                titulo = dtoItem.titulo,
                entidad = dtoItem.entidad,
                descripcion = dtoItem.descripcion,
                costo = dtoItem.costo,
                tiempoEstimado = dtoItem.tiempoEstimado,
                icono = dtoItem.icono,
                lastVisited = existing?.lastVisited, // Preservar
                isQuickAccess = dtoItem.isQuickAccess ?: false,
                isCompleted = existing?.isCompleted ?: false // Preservar
            )
        }

        val requisitos = dto.requisitos.map { dtoItem ->
            val existing = existingRequisitos[dtoItem.id]
            RequisitoEntity(
                id = dtoItem.id,
                tramiteId = dtoItem.tramiteId,
                descripcion = dtoItem.descripcion,
                esTramitePrevio = dtoItem.esTramitePrevio,
                isChecked = existing?.isChecked ?: false, // Preservar
                nota = dtoItem.nota
            )
        }

        val pasos = dto.pasos.map { 
            PasoEntity(
                id = it.id,
                tramiteId = it.tramiteId,
                orden = it.orden,
                descripcion = it.descripcion
            )
        }

        val delegaciones = dto.delegaciones.map { 
            DelegacionEntity(
                id = it.id,
                nombreOficina = it.nombreOficina,
                departamento = it.departamento,
                latitud = it.latitud,
                longitud = it.longitud,
                direccion = it.direccion
            )
        }

        val crossRefs = dto.delegacionTramites.map { 
            DelegacionTramiteCrossRef(it.delegacionId, it.tramiteId)
        }

        // Validación de integridad referencial
        val categoriaIds = categorias.map { it.id }.toSet()
        val tramiteIds = tramites.map { it.id }.toSet()
        val delegacionIds = delegaciones.map { it.id }.toSet()

        val validTramites = tramites.filter { it.categoriaId in categoriaIds }
        val validRequisitos = requisitos.filter { it.tramiteId in tramiteIds }
        val validPasos = pasos.filter { it.tramiteId in tramiteIds }
        val validCrossRefs = crossRefs.filter { it.tramiteId in tramiteIds && it.delegacionId in delegacionIds }

        tramiteDao.applySnapshot(
            categorias,
            validTramites,
            validRequisitos,
            validPasos,
            delegaciones,
            validCrossRefs
        )
    }
}
