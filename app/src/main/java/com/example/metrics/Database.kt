package com.example.metrics

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "metrics")
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "daily_metric_entries",
    primaryKeys = ["metricId", "date"]
)
data class DailyMetricEntry(
    val metricId: Long,
    val date: String,
    val isAchieved: Boolean
)

@Dao
interface MetricDao {

    @Query("SELECT * FROM metrics ORDER BY id")
    fun getAllMetrics(): Flow<List<MetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: MetricEntity)

    @Query("DELETE FROM metrics WHERE id = :metricId")
    suspend fun deleteMetric(metricId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyEntry(entry: DailyMetricEntry)

    @Query("SELECT * FROM daily_metric_entries WHERE date = :date")
    fun getEntriesForDate(date: String): Flow<List<DailyMetricEntry>>

    @Query("""
        SELECT * FROM daily_metric_entries
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC
    """)
    fun getEntriesBetween(
        startDate: String,
        endDate: String
    ): Flow<List<DailyMetricEntry>>

    @Query("DELETE FROM daily_metric_entries WHERE metricId = :metricId")
    suspend fun deleteEntriesForMetric(metricId: Long)
}

@Database(
    entities = [
        MetricEntity::class,
        DailyMetricEntry::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun metricDao(): MetricDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    "ALTER TABLE metrics RENAME TO old_metrics"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metrics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_metric_entries (
                        metricId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        isAchieved INTEGER NOT NULL,
                        PRIMARY KEY(metricId, date)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "INSERT INTO metrics (id, name) SELECT id, name FROM old_metrics"
                )

                db.execSQL(
                    """
                    INSERT INTO daily_metric_entries
                    (metricId, date, isAchieved)
                    SELECT
                        id,
                        date('now', 'localtime'),
                        isAchieved
                    FROM old_metrics
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE old_metrics")
            }
        }

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "metrics_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}