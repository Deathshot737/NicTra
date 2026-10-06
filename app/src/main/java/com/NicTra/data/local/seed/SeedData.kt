package com.nictra.data.local.seed

import com.nictra.data.local.entities.*

data class SeedData(
    val categorias: List<CategoriaEntity>,
    val tramites: List<TramiteEntity>,
    val requisitos: List<RequisitoEntity>,
    val pasos: List<PasoEntity>,
    val delegaciones: List<DelegacionEntity>
)
