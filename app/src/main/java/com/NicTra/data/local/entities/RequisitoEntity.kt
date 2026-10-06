package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "requisitos",
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
data class RequisitoEntity(
    @PrimaryKey val id: String,
    val tramiteId: String,
    val descripcion: String,
    val esTramitePrevio: Boolean,
    val tramiteDestinoId: String? = null,
    val isChecked: Boolean = false,
    val nota: String? = null
)
