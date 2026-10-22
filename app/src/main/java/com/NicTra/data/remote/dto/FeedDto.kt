package com.nictra.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeedDto(
    val categorias: List<CategoriaDto>?,
    val tramites: List<TramiteDto>?,
    val requisitos: List<RequisitoDto>?,
    val pasos: List<PasoDto>?,
    val delegaciones: List<DelegacionDto>?,
    @SerializedName("delegacion_tramites")
    val delegacionTramites: List<DelegacionTramiteDto>?
)

data class CategoriaDto(
    val id: String,
    val nombre: String,
    val icono: String
)

data class TramiteDto(
    val id: String,
    val categoriaId: String,
    val titulo: String,
    val entidad: String,
    val descripcion: String,
    val costo: String,
    val tiempoEstimado: String,
    val icono: String,
    val isQuickAccess: Boolean? = false
)

data class RequisitoDto(
    val id: String,
    val tramiteId: String,
    val descripcion: String,
    val esTramitePrevio: Boolean,
    val nota: String? = null
)

data class PasoDto(
    val id: String,
    val tramiteId: String,
    val orden: Int,
    val descripcion: String
)

data class DelegacionDto(
    val id: String,
    val nombreOficina: String,
    val departamento: String,
    val latitud: Double,
    val longitud: Double,
    val direccion: String
)

data class DelegacionTramiteDto(
    val delegacionId: String,
    val tramiteId: String
)

data class FeedVersionDto(
    val version: String,
    val actualizadoEn: String
)
