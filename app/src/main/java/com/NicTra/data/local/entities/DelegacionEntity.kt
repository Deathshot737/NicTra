package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "delegaciones")
data class DelegacionEntity(
    @PrimaryKey val id: String,
    val nombreOficina: String,
    val departamento: String,
    val latitud: Double,
    val longitud: Double,
    val direccion: String
)
