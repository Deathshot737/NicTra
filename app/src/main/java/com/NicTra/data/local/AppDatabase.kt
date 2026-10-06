package com.nictra.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import com.nictra.data.local.seed.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoriaEntity::class,
        TramiteEntity::class,
        RequisitoEntity::class,
        PasoEntity::class,
        DelegacionEntity::class
    ],
    version = 55, // Versión 55: Catálogo de 20 trámites con requisitos y pasos completos
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tramiteDao(): TramiteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nictra_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                
                applicationScope.launch {
                    Log.i("NicTraDB", "Instalando catálogo completo de trámites detallados...")
                    DatabaseSeeder.seedDatabase(context.applicationContext, instance.tramiteDao())
                }

                instance
            }
        }
    }
}
