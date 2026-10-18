package com.example.driveguard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores static layout details for an intersection (VRI node in NDW/TLEX).
 */
@Entity(tableName = "intersections")
data class IntersectionEntity(
    @PrimaryKey
    val id: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "region_topic_tag")
    val regionTopicTag: String
)
