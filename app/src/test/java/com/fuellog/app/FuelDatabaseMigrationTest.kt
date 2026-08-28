package com.fuellog.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuellog.app.data.EnergyType
import com.fuellog.app.data.FuelDatabase
import com.fuellog.app.data.FuelRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FuelDatabaseMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test fun versionOneMigrationChainPreservesLegacyFuelDataAndAcceptsUnknownOdometer() = runBlocking {
        val databaseName = "fuel-unit-migration-v1-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        createLegacyRecordsTable(legacy)
        legacy.execSQL("INSERT INTO vehicles(id, name, createdAt) VALUES(7, '旧燃油车', 123)")
        legacy.execSQL("INSERT INTO fuel_records(id, vehicleId, odometerKm, fuelGrade, pricePerLiter, amountPaid, liters, timestamp) VALUES(9, 7, 1000.25, '95', 8.2, 49.2, 6, 456)")
        legacy.version = 1
        legacy.close()

        val db = openCurrent(databaseName)
        try {
            val vehicle = db.dao().vehiclesOnce().single()
            val oldRecord = db.dao().recordsOnce(vehicle.id).single()
            assertEquals(7L, vehicle.id)
            assertEquals("旧燃油车", vehicle.name)
            assertEquals(123L, vehicle.createdAt)
            assertEquals(EnergyType.FUEL, vehicle.energyType)
            assertNull(vehicle.energyCapacity)
            assertEquals(9L, oldRecord.id)
            assertEquals(1000.25, oldRecord.odometerKm!!, 0.0)
            assertEquals("95", oldRecord.fuelGrade)
            assertEquals(8.2, oldRecord.pricePerLiter, 0.0)
            assertEquals(49.2, oldRecord.amountPaid, 0.0)
            assertEquals(6.0, oldRecord.liters, 0.0)
            assertEquals(456L, oldRecord.timestamp)

            db.dao().insertRecord(FuelRecord(
                vehicleId = vehicle.id,
                odometerKm = null,
                fuelGrade = "92",
                pricePerLiter = 7.8,
                amountPaid = 46.8,
                liters = 6.0,
                timestamp = 789
            ))
            assertNull(db.dao().recordsOnce(vehicle.id).last().odometerKm)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun versionTwoMigrationChainPreservesElectricTypeAndAddsNullCapacityAndNullableOdometer() = runBlocking {
        val databaseName = "fuel-unit-migration-v2-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, energyType TEXT NOT NULL DEFAULT 'FUEL')")
        createLegacyRecordsTable(legacy)
        legacy.execSQL("INSERT INTO vehicles(id, name, createdAt, energyType) VALUES(11, '旧电动车', 321, 'ELECTRIC')")
        legacy.execSQL("INSERT INTO fuel_records(id, vehicleId, odometerKm, fuelGrade, pricePerLiter, amountPaid, liters, timestamp) VALUES(12, 11, 2000, 'HOME', 0.5, 20, 40, 654)")
        legacy.version = 2
        legacy.close()

        val db = openCurrent(databaseName)
        try {
            val vehicle = db.dao().vehiclesOnce().single()
            val record = db.dao().recordsOnce(vehicle.id).single()
            assertEquals(EnergyType.ELECTRIC, vehicle.energyType)
            assertNull(vehicle.energyCapacity)
            assertEquals(2000.0, record.odometerKm!!, 0.0)
            assertEquals("HOME", record.fuelGrade)
            assertEquals(40.0, record.liters, 0.0)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun versionThreeToFourPreservesEveryVehicleAndRecordFieldExactly() = runBlocking {
        val databaseName = "fuel-unit-migration-v3-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, energyType TEXT NOT NULL DEFAULT 'FUEL', energyCapacity REAL DEFAULT NULL)")
        createLegacyRecordsTable(legacy)
        legacy.execSQL("INSERT INTO vehicles(id, name, createdAt, energyType, energyCapacity) VALUES(21, '有容量电动车', 987, 'ELECTRIC', 58.5)")
        legacy.execSQL("INSERT INTO fuel_records(id, vehicleId, odometerKm, fuelGrade, pricePerLiter, amountPaid, liters, timestamp) VALUES(31, 21, 12345.67, 'PUBLIC', 1.38, 27.6, 20, 2468)")
        legacy.version = 3
        legacy.close()

        val db = Room.databaseBuilder(context, FuelDatabase::class.java, databaseName)
            .addMigrations(FuelDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val vehicle = db.dao().vehiclesOnce().single()
            val record = db.dao().recordsOnce(vehicle.id).single()
            assertEquals(21L, vehicle.id)
            assertEquals("有容量电动车", vehicle.name)
            assertEquals(987L, vehicle.createdAt)
            assertEquals(EnergyType.ELECTRIC, vehicle.energyType)
            assertEquals(58.5, vehicle.energyCapacity!!, 0.0)
            assertEquals(31L, record.id)
            assertEquals(21L, record.vehicleId)
            assertEquals(12345.67, record.odometerKm!!, 0.0)
            assertEquals("PUBLIC", record.fuelGrade)
            assertEquals(1.38, record.pricePerLiter, 0.0)
            assertEquals(27.6, record.amountPaid, 0.0)
            assertEquals(20.0, record.liters, 0.0)
            assertEquals(2468L, record.timestamp)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun openCurrent(databaseName: String): FuelDatabase =
        Room.databaseBuilder(context, FuelDatabase::class.java, databaseName)
            .addMigrations(
                FuelDatabase.MIGRATION_1_2,
                FuelDatabase.MIGRATION_2_3,
                FuelDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

    private fun createLegacyRecordsTable(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE fuel_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vehicleId INTEGER NOT NULL, odometerKm REAL NOT NULL, fuelGrade TEXT NOT NULL, pricePerLiter REAL NOT NULL, amountPaid REAL NOT NULL, liters REAL NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_fuel_records_vehicleId ON fuel_records(vehicleId)")
    }
}
