package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "categorias")
data class CategoriaEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val icono: String
)
