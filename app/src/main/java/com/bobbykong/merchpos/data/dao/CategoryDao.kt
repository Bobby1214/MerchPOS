package com.bobbykong.merchpos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.bobbykong.merchpos.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sort_order")
    suspend fun getAll(): List<Category>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM categories")
    suspend fun maxOrder(): Int

    // IGNORE: a category that already has a place keeps it
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(category: Category)

    @Upsert
    suspend fun upsertAll(categories: List<Category>)
}
