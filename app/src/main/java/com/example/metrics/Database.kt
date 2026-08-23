package com.example.metrics

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "metrics")
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isAchieved: Boolean = false
)

@Dao
interface MetricDao {
    @Query("SELECT * FROM metrics")
    fun getAllMetrics(): Flow<List<MetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: MetricEntity)

    @Update
    suspend fun updateMetric(metric: MetricEntity)

    @Delete
    suspend fun deleteMetric(metric: MetricEntity)
}

@Database(entities = [MetricEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun metricDao(): MetricDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "metrics_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}