package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bobbykong.merchpos.data.model.Event
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY start_date DESC")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE end_date IS NULL ORDER BY start_date DESC LIMIT 1")
    fun observeOpen(): Flow<Event?>

    @Query("SELECT * FROM events WHERE end_date IS NULL ORDER BY start_date DESC LIMIT 1")
    suspend fun getOpen(): Event?

    @Insert
    suspend fun insert(event: Event): Long

    @Query("UPDATE events SET end_date = :endDate WHERE id = :id AND end_date IS NULL")
    suspend fun end(id: Int, endDate: Long): Int
}
