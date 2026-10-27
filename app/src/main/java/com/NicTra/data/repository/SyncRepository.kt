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

    suspend fun syncFeed(force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                Log.d("SyncRepo", ">>> Iniciando verificación de versión en la API...")
                
                // 1. Preguntar SIEMPRE primero la versión a la API
                val remoteInfo = apiService.getFeedVersion()
                val localVersion = syncPreferences.feedVersion.first()
                
                Log.d("SyncRepo", "Versión remota: ${remoteInfo.version} | Versión local: ${localVersion ?: "Ninguna"}")

                // 2. Decisión: ¿Es necesario descargar el feed completo?
                if (!force && localVersion != null && remoteInfo.version == localVersion) {
                    Log.i("SyncRepo", "El feed ya está actualizado (v${remoteInfo.version}). Omitiendo descarga.")
                    return@withContext Result.success(Unit)
                }

                // 3. Descarga solo si la versión cambió o si es forzado
                Log.i("SyncRepo", "Descargando feed completo desde la API (Motivo: Versión nueva o Force)...")
                val snapshot = apiService.getFeed()

                // 4. Procesar y guardar en base de datos
                applySnapshot(snapshot)

                // 5. Actualizar versión local SOLO si todo lo anterior tuvo éxito
                syncPreferences.saveSyncInfo(
                    version = remoteInfo.version, 
                    fecha = remoteInfo.actualizadoEn,
                    fullSync = true
                )
                
                Log.i("SyncRepo", "Sincronización v${remoteInfo.version} completada con éxito.")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SyncRepo", "Error en sincronización: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun applySnapshot(dto: FeedDto) {
        if (dto.categorias == null || dto.tramites == null) {
             throw IllegalArgumentException("Snapshot de API inválido: faltan campos obligatorios")
        }

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
                lastVisited = existing?.lastVisited,
                isQuickAccess = dtoItem.isQuickAccess ?: false,
                isCompleted = existing?.isCompleted ?: false
            )
        }

        val requisitos = (dto.requisitos ?: emptyList()).map { dtoItem ->
            val existing = existingRequisitos[dtoItem.id]
            RequisitoEntity(
                id = dtoItem.id,
                tramiteId = dtoItem.tramiteId,
                descripcion = dtoItem.descripcion,
                esTramitePrevio = dtoItem.esTramitePrevio,
                isChecked = existing?.isChecked ?: false,
                nota = dtoItem.nota
            )
        }

        val pasos = (dto.pasos ?: emptyList()).map { 
            PasoEntity(it.id, it.tramiteId, it.orden, it.descripcion)
        }

        val delegaciones = (dto.delegaciones ?: emptyList()).map { 
            DelegacionEntity(it.id, it.nombreOficina, it.departamento, it.latitud, it.longitud, it.direccion)
        }

        val crossRefs = (dto.delegacionTramites ?: emptyList()).map { 
            DelegacionTramiteCrossRef(it.delegacionId, it.tramiteId)
        }

        tramiteDao.applySnapshot(categorias, tramites, requisitos, pasos, delegaciones, crossRefs)
    }
}
