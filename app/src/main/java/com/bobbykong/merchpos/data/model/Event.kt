package com.bobbykong.merchpos.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A convention or market. Every order belongs to one event, so reports can be split per event. */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: Long,
    // null = event is still running
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
)
