package com.fuellog.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class EnergyType(val emoji: String) {
    FUEL("⛽"),
    ELECTRIC("⚡")
}

class EnergyTypeConverters {
    @TypeConverter
    fun fromEnergyType(value: EnergyType): String = value.name

    @TypeConverter
    fun toEnergyType(value: String): EnergyType = EnergyType.valueOf(value)
}

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'FUEL'") val energyType: EnergyType = EnergyType.FUEL,
    @ColumnInfo(defaultValue = "NULL") val energyCapacity: Double? = null
)

@Entity(
    tableName = "fuel_records",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class,
        parentColumns = ["id"],
        childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId")]
)
data class FuelRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val odometerKm: Double,
    val fuelGrade: String,
    val pricePerLiter: Double,
    val amountPaid: Double,
    val liters: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FuelDao {
    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC") fun vehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC") suspend fun vehiclesOnce(): List<Vehicle>
    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1") suspend fun vehicleOnce(id: Long): Vehicle?
    @Insert suspend fun insertVehicle(vehicle: Vehicle): Long
    @Query(
        """UPDATE vehicles
           SET name = :name, energyType = :energyType, energyCapacity = :energyCapacity
           WHERE id = :id
             AND (energyType = :energyType OR NOT EXISTS (
                 SELECT 1 FROM fuel_records WHERE vehicleId = :id LIMIT 1
             ))"""
    )
    suspend fun updateVehicleInfo(
        id: Long,
        name: String,
        energyType: EnergyType,
        energyCapacity: Double?
    ): Int
    @Query("DELETE FROM vehicles WHERE id = :id") suspend fun deleteVehicle(id: Long)

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY timestamp ASC, id ASC")
    fun records(vehicleId: Long): Flow<List<FuelRecord>>
    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY timestamp ASC, id ASC")
    suspend fun recordsOnce(vehicleId: Long): List<FuelRecord>
    @Query("SELECT * FROM fuel_records WHERE id = :id LIMIT 1")
    suspend fun recordOnce(id: Long): FuelRecord?
    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId AND fuelGrade = :fuelGrade ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun latestRecordForGrade(vehicleId: Long, fuelGrade: String): Flow<FuelRecord?>
    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestRecord(vehicleId: Long): FuelRecord?
    @Query("SELECT COUNT(*) FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun recordCount(vehicleId: Long): Int
    @Insert suspend fun insertRecord(record: FuelRecord): Long
    @Update suspend fun updateRecord(record: FuelRecord)
    @Query("UPDATE fuel_records SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateRecordTimestamp(id: Long, timestamp: Long)
    @Query("DELETE FROM fuel_records WHERE id = :id") suspend fun deleteRecord(id: Long)
}

@Database(entities = [Vehicle::class, FuelRecord::class], version = 3, exportSchema = false)
@TypeConverters(EnergyTypeConverters::class)
abstract class FuelDatabase : RoomDatabase() {
    abstract fun dao(): FuelDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE vehicles ADD COLUMN energyType TEXT NOT NULL DEFAULT 'FUEL'"
                )
                // Every pre-2.0 vehicle is a fuel vehicle. Keep this assignment explicit.
                database.execSQL("UPDATE vehicles SET energyType = 'FUEL'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE vehicles ADD COLUMN energyCapacity REAL DEFAULT NULL"
                )
            }
        }

        fun create(context: Context): FuelDatabase = Room.databaseBuilder(
            context, FuelDatabase::class.java, "fuel-log.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
