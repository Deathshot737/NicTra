package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "tramites",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoriaId"])]
)
data class TramiteEntity(
    @PrimaryKey val id: String,
    val categoriaId: String,
    val titulo: String,
    val entidad: String,
    val descripcion: String,
    val costo: String,
    val tiempoEstimado: String,
    val icono: String = "",
    val lastVisited: Long? = null,
    val isQuickAccess: Boolean = false,
    val isCompleted: Boolean = false
)
