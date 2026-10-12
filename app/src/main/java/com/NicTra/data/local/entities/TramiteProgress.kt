package com.nictra.data.local.entities

import androidx.room.Embedded

data class TramiteProgress(
    @Embedded val tramite: TramiteEntity,
    val totalRequisitos: Int,
    val checkedRequisitos: Int
)
