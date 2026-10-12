package com.nictra.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Keep
@Entity(
    tableName = "delegacion_tramites",
    primaryKeys = ["delegacionId", "tramiteId"],
    foreignKeys = [
        ForeignKey(
            entity = DelegacionEntity::class,
            parentColumns = ["id"],
            childColumns = ["delegacionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TramiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["tramiteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["delegacionId"]),
        Index(value = ["tramiteId"])
    ]
)
data class DelegacionTramiteCrossRef(
    val delegacionId: String,
    val tramiteId: String
)
