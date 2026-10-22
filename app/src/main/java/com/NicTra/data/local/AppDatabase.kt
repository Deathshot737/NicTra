package com.nictra.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nictra.data.local.dao.TramiteDao
import com.nictra.data.local.entities.*
import kotlinx.coroutines.CoroutineScope

@Database(
    entities = [
        CategoriaEntity::class,
        TramiteEntity::class,
        RequisitoEntity::class,
        PasoEntity::class,
        DelegacionEntity::class,
        DelegacionTramiteCrossRef::class
    ],
    version = 221,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tramiteDao(): TramiteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                instance
            }
        }
    }
}
