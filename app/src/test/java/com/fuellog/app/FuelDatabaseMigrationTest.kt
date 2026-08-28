package com.fuellog.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuellog.app.data.EnergyType
import com.fuellog.app.data.FuelDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FuelDatabaseMigrationTest {
    @Test fun versionTwoMigrationPreservesVehicleAndRecordAndAddsNullCapacity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fuel-unit-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, energyType TEXT NOT NULL DEFAULT 'FUEL')")
        legacy.execSQL("CREATE TABLE fuel_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vehicleId INTEGER NOT NULL, odometerKm REAL NOT NULL, fuelGrade TEXT NOT NULL, pricePerLiter REAL NOT NULL, amountPaid REAL NOT NULL, liters REAL NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        legacy.execSQL("CREATE INDEX index_fuel_records_vehicleId ON fuel_records(vehicleId)")
        legacy.execSQL("INSERT INTO vehicles(id, name, createdAt, energyType) VALUES(11, '旧电动车', 321, 'ELECTRIC')")
        legacy.execSQL("INSERT INTO fuel_records(id, vehicleId, odometerKm, fuelGrade, pricePerLiter, amountPaid, liters, timestamp) VALUES(12, 11, 2000, 'HOME', 0.5, 20, 40, 654)")
        legacy.version = 2
        legacy.close()

        val db = Room.databaseBuilder(context, FuelDatabase::class.java, databaseName)
            .addMigrations(FuelDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val vehicle = db.dao().vehiclesOnce().single()
            val record = db.dao().recordsOnce(vehicle.id).single()
            assertEquals(11L, vehicle.id)
            assertEquals("旧电动车", vehicle.name)
            assertEquals(321L, vehicle.createdAt)
            assertEquals(EnergyType.ELECTRIC, vehicle.energyType)
            assertNull(vehicle.energyCapacity)
            assertEquals(12L, record.id)
            assertEquals(11L, record.vehicleId)
            assertEquals("HOME", record.fuelGrade)
            assertEquals(40.0, record.liters, 0.001)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}
