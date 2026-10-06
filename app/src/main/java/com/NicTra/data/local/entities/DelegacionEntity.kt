package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "delegaciones",
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
data class DelegacionEntity(
    @PrimaryKey val id: String,
    val tramiteId: String,
    val nombreOficina: String,
    val departamento: String,
    val latitud: Double,
    val longitud: Double,
    val direccion: String
)
