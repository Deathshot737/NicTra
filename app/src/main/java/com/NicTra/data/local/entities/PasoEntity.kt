package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "pasos",
    foreignKeys = [
        ForeignKey(
            entity = TramiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["tramiteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tramiteId"])]
)
data class PasoEntity(
    @PrimaryKey val id: String,
    val tramiteId: String,
    val orden: Int,
    val descripcion: String,
    val accionUrl: String? = null
)
