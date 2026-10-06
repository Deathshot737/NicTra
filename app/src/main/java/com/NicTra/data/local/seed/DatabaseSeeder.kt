package com.nictra.data.local.seed

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import org.json.JSONObject

@Keep
object DatabaseSeeder {

    suspend fun seedDatabase(context: Context, tramiteDao: TramiteDao) {
        try {
            val anyCategoria = tramiteDao.getAnyCategoria()
            if (anyCategoria != null) {
                Log.i("NicTraDB", "Datos detectados. Omitiendo siembra.")
                return
            }

            Log.i("NicTraDB", "Iniciando carga de datos completa...")
            val jsonString = context.assets.open("tramites_seed.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            // Categorías
            val categoriasJson = jsonObject.optJSONArray("categorias")
            val categoriasList = mutableListOf<CategoriaEntity>()
            categoriasJson?.let {
                for (i in 0 until it.length()) {
                    val obj = it.getJSONObject(i)
                    categoriasList.add(CategoriaEntity(obj.getString("id"), obj.getString("nombre"), obj.getString("icono")))
                }
            }

            // Trámites
            val tramitesJson = jsonObject.optJSONArray("tramites")
            val tramitesList = mutableListOf<TramiteEntity>()
            tramitesJson?.let {
                for (i in 0 until it.length()) {
                    val obj = it.getJSONObject(i)
                    tramitesList.add(TramiteEntity(
                        id = obj.getString("id"),
                        categoriaId = obj.getString("categoriaId"),
                        titulo = obj.getString("titulo"),
                        entidad = obj.optString("entidad", ""),
                        descripcion = obj.optString("descripcion", ""),
                        costo = obj.optString("costo", ""),
                        tiempoEstimado = obj.optString("tiempoEstimado", ""),
                        icono = obj.optString("icono", ""),
                        isQuickAccess = obj.optBoolean("isQuickAccess", false),
                        isCompleted = false
                    ))
                }
            }

            // Requisitos
            val requisitosJson = jsonObject.optJSONArray("requisitos")
            val requisitosList = mutableListOf<RequisitoEntity>()
            requisitosJson?.let {
                for (i in 0 until it.length()) {
                    val obj = it.getJSONObject(i)
                    requisitosList.add(RequisitoEntity(
                        id = obj.getString("id"),
                        tramiteId = obj.getString("tramiteId"),
                        descripcion = obj.getString("descripcion"),
                        esTramitePrevio = obj.optBoolean("esTramitePrevio", false),
                        isChecked = false,
                        nota = if (obj.isNull("nota")) null else obj.getString("nota")
                    ))
                }
            }

            // Pasos (¡Añadido de nuevo!)
            val pasosJson = jsonObject.optJSONArray("pasos")
            val pasosList = mutableListOf<PasoEntity>()
            pasosJson?.let {
                for (i in 0 until it.length()) {
                    val obj = it.getJSONObject(i)
                    pasosList.add(PasoEntity(
                        id = obj.getString("id"),
                        tramiteId = obj.getString("tramiteId"),
                        orden = obj.getInt("orden"),
                        descripcion = obj.getString("descripcion")
                    ))
                }
            }

            // Delegaciones (¡Añadido de nuevo!)
            val delegacionesJson = jsonObject.optJSONArray("delegaciones")
            val delegacionesList = mutableListOf<DelegacionEntity>()
            delegacionesJson?.let {
                for (i in 0 until it.length()) {
                    val obj = it.getJSONObject(i)
                    delegacionesList.add(DelegacionEntity(
                        id = obj.getString("id"),
                        tramiteId = obj.getString("tramiteId"),
                        nombreOficina = obj.getString("nombreOficina"),
                        departamento = obj.getString("departamento"),
                        latitud = obj.optDouble("latitud", 0.0),
                        longitud = obj.optDouble("longitud", 0.0),
                        direccion = obj.getString("direccion")
                    ))
                }
            }

            if (categoriasList.isNotEmpty()) tramiteDao.insertCategorias(categoriasList)
            if (tramitesList.isNotEmpty()) tramiteDao.insertTramites(tramitesList)
            if (requisitosList.isNotEmpty()) tramiteDao.insertRequisitos(requisitosList)
            if (pasosList.isNotEmpty()) tramiteDao.insertPasos(pasosList)
            if (delegacionesList.isNotEmpty()) tramiteDao.insertDelegaciones(delegacionesList)
            
            Log.i("NicTraDB", "SIEMBRA COMPLETADA EXITOSAMENTE.")
        } catch (e: Exception) {
            Log.e("NicTraDB", "ERROR EN SIEMBRA: ${e.message}")
        }
    }
}
