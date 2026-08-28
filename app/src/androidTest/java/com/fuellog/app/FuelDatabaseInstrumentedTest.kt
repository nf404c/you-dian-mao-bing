package com.fuellog.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuellog.app.data.*
import com.fuellog.app.domain.Consumption
import com.fuellog.app.domain.validateRecordInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuelDatabaseInstrumentedTest {
    @Test fun vehicleRenameUpdatesSameRowAndKeepsFuelRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val vehicleId = dao.insertVehicle(Vehicle(name = "旧名称", createdAt = 123L))
            dao.insertRecord(FuelRecord(vehicleId = vehicleId, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 8.0, amountPaid = 48.0, liters = 6.0))

            assertEquals(1, dao.updateVehicleInfo(vehicleId, "新名称", EnergyType.FUEL, 8.0))

            val updated = dao.vehiclesOnce().single()
            assertEquals(vehicleId, updated.id)
            assertEquals("新名称", updated.name)
            assertEquals(123L, updated.createdAt)
            assertEquals(8.0, updated.energyCapacity!!, 0.001)
            assertEquals(1, dao.recordCount(vehicleId))
        } finally {
            db.close()
        }
    }

    @Test fun migrationFromVersionOneExplicitlyMarksOldVehiclesAsFuelAndKeepsRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fuel-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL("CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE fuel_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vehicleId INTEGER NOT NULL, odometerKm REAL NOT NULL, fuelGrade TEXT NOT NULL, pricePerLiter REAL NOT NULL, amountPaid REAL NOT NULL, liters REAL NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        legacy.execSQL("CREATE INDEX index_fuel_records_vehicleId ON fuel_records(vehicleId)")
        legacy.execSQL("INSERT INTO vehicles(id, name, createdAt) VALUES(7, '旧燃油车', 123)")
        legacy.execSQL("INSERT INTO fuel_records(id, vehicleId, odometerKm, fuelGrade, pricePerLiter, amountPaid, liters, timestamp) VALUES(9, 7, 1000, '95', 8.2, 49.2, 6, 456)")
        legacy.version = 1
        legacy.close()

        val db = Room.databaseBuilder(context, FuelDatabase::class.java, databaseName)
            .addMigrations(FuelDatabase.MIGRATION_1_2, FuelDatabase.MIGRATION_2_3)
            .build()
        try {
            val vehicle = db.dao().vehiclesOnce().single()
            val record = db.dao().recordsOnce(vehicle.id).single()
            assertEquals(7L, vehicle.id)
            assertEquals("旧燃油车", vehicle.name)
            assertEquals(123L, vehicle.createdAt)
            assertEquals(EnergyType.FUEL, vehicle.energyType)
            assertNull(vehicle.energyCapacity)
            assertEquals(9L, record.id)
            assertEquals("95", record.fuelGrade)
            assertEquals(6.0, record.liters, 0.001)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun migrationFromVersionTwoAddsNullCapacityWithoutChangingVehiclesOrRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "fuel-migration-v2-${System.nanoTime()}.db"
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

    @Test fun vehicleEnergyTypeCanChangeOnlyBeforeItsFirstRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val vehicleId = dao.insertVehicle(Vehicle(name = "空车", energyType = EnergyType.FUEL))
            assertEquals(1, dao.updateVehicleInfo(vehicleId, "空车", EnergyType.ELECTRIC, 60.0))
            assertEquals(EnergyType.ELECTRIC, dao.vehicleOnce(vehicleId)!!.energyType)
            dao.insertRecord(FuelRecord(vehicleId = vehicleId, odometerKm = 100.0, fuelGrade = "HOME", pricePerLiter = 0.55, amountPaid = 5.5, liters = 10.0))
            assertEquals(0, dao.updateVehicleInfo(vehicleId, "尝试改回", EnergyType.FUEL, 8.0))
            val locked = dao.vehicleOnce(vehicleId)!!
            assertEquals("空车", locked.name)
            assertEquals(EnergyType.ELECTRIC, locked.energyType)
            assertEquals(1, dao.updateVehicleInfo(vehicleId, "只改名称", EnergyType.ELECTRIC, 58.5))
            assertEquals("只改名称", dao.vehicleOnce(vehicleId)!!.name)
            assertEquals(58.5, dao.vehicleOnce(vehicleId)!!.energyCapacity!!, 0.001)
        } finally {
            db.close()
        }
    }

    @Test fun fuelAndElectricVehiclesKeepRecordsAndRememberedPricesIndependent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val fuel = dao.insertVehicle(Vehicle(name = "油车", energyType = EnergyType.FUEL))
            val electric = dao.insertVehicle(Vehicle(name = "电车", energyType = EnergyType.ELECTRIC))
            dao.insertRecord(FuelRecord(vehicleId = fuel, odometerKm = 100.0, fuelGrade = "92", pricePerLiter = 7.8, amountPaid = 39.0, liters = 5.0, timestamp = 10))
            dao.insertRecord(FuelRecord(vehicleId = fuel, odometerKm = 200.0, fuelGrade = "95", pricePerLiter = 8.3, amountPaid = 49.8, liters = 6.0, timestamp = 20))
            dao.insertRecord(FuelRecord(vehicleId = electric, odometerKm = 1000.0, fuelGrade = "HOME", pricePerLiter = 0.55, amountPaid = 11.0, liters = 20.0, timestamp = 30))
            dao.insertRecord(FuelRecord(vehicleId = electric, odometerKm = 1200.0, fuelGrade = "PUBLIC", pricePerLiter = 1.38, amountPaid = 27.6, liters = 20.0, timestamp = 40))

            assertEquals(listOf("92", "95"), dao.recordsOnce(fuel).map { it.fuelGrade })
            assertEquals(listOf("HOME", "PUBLIC"), dao.recordsOnce(electric).map { it.fuelGrade })
            assertEquals(7.8, dao.latestRecordForGrade(fuel, "92").first()!!.pricePerLiter, 0.001)
            assertNull(dao.latestRecordForGrade(fuel, "HOME").first())
            assertEquals(0.55, dao.latestRecordForGrade(electric, "HOME").first()!!.pricePerLiter, 0.001)
            assertEquals(1.38, dao.latestRecordForGrade(electric, "PUBLIC").first()!!.pricePerLiter, 0.001)
            assertNull(dao.latestRecordForGrade(electric, "95").first())
        } finally {
            db.close()
        }
    }

    @Test fun recordEditorUpdatePersistsAllEditableFieldsInMemory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val vehicle = dao.insertVehicle(Vehicle(name = "A"))
            val id = dao.insertRecord(FuelRecord(vehicleId = vehicle, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 8.0, amountPaid = 48.0, liters = 6.0, timestamp = 10))
            dao.updateRecord(FuelRecord(id = id, vehicleId = vehicle, odometerKm = 120.0, fuelGrade = "92", pricePerLiter = 7.5, amountPaid = 45.0, liters = 6.0, timestamp = 20))
            val updated = dao.records(vehicle).first().single()
            assertEquals(120.0, updated.odometerKm, 0.001)
            assertEquals("92", updated.fuelGrade)
            assertEquals(7.5, updated.pricePerLiter, 0.001)
            assertEquals(45.0, updated.amountPaid, 0.001)
            assertEquals(6.0, updated.liters, 0.001)
            assertEquals(20, updated.timestamp)
        } finally {
            db.close()
        }
    }

    @Test fun timestampUpdateReordersRecordsAndChangesLatestPriceInMemory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val vehicle = dao.insertVehicle(Vehicle(name = "A"))
            val first = dao.insertRecord(FuelRecord(vehicleId = vehicle, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 8.31, amountPaid = 49.86, liters = 6.0, timestamp = 10))
            val second = dao.insertRecord(FuelRecord(vehicleId = vehicle, odometerKm = 200.0, fuelGrade = "95", pricePerLiter = 8.40, amountPaid = 50.40, liters = 6.0, timestamp = 20))
            assertEquals(listOf(first, second), dao.records(vehicle).first().map { it.id })
            assertEquals(8.40, dao.latestRecordForGrade(vehicle, "95").first()!!.pricePerLiter, 0.001)

            dao.updateRecordTimestamp(second, 5)
            assertEquals(listOf(second, first), dao.records(vehicle).first().map { it.id })
            assertEquals(8.31, dao.latestRecordForGrade(vehicle, "95").first()!!.pricePerLiter, 0.001)
        } finally {
            db.close()
        }
    }

    @Test fun recentGradePricesAreVehicleScopedAndFallBackAfterDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val firstVehicle = dao.insertVehicle(Vehicle(name = "A"))
            val secondVehicle = dao.insertVehicle(Vehicle(name = "B"))
            val old95 = dao.insertRecord(FuelRecord(vehicleId = firstVehicle, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 8.31, amountPaid = 49.86, liters = 6.0, timestamp = 10))
            dao.insertRecord(FuelRecord(vehicleId = firstVehicle, odometerKm = 200.0, fuelGrade = "92", pricePerLiter = 7.80, amountPaid = 46.80, liters = 6.0, timestamp = 20))
            val new95 = dao.insertRecord(FuelRecord(vehicleId = firstVehicle, odometerKm = 300.0, fuelGrade = "95", pricePerLiter = 8.40, amountPaid = 50.40, liters = 6.0, timestamp = 30))
            dao.insertRecord(FuelRecord(vehicleId = secondVehicle, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 8.06, amountPaid = 48.36, liters = 6.0, timestamp = 40))

            assertEquals(8.40, dao.latestRecordForGrade(firstVehicle, "95").first()!!.pricePerLiter, 0.001)
            assertEquals(7.80, dao.latestRecordForGrade(firstVehicle, "92").first()!!.pricePerLiter, 0.001)
            assertEquals(8.06, dao.latestRecordForGrade(secondVehicle, "95").first()!!.pricePerLiter, 0.001)
            assertNull(dao.latestRecordForGrade(secondVehicle, "92").first())

            dao.deleteRecord(new95)
            assertEquals(8.31, dao.latestRecordForGrade(firstVehicle, "95").first()!!.pricePerLiter, 0.001)
            dao.deleteRecord(old95)
            assertNull(dao.latestRecordForGrade(firstVehicle, "95").first())
        } finally {
            db.close()
        }
    }

    @Test fun deletingVehiclesCascadesRecordsWithoutAffectingOthers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val first = dao.insertVehicle(Vehicle(name = "A"))
            val second = dao.insertVehicle(Vehicle(name = "B"))
            dao.insertRecord(FuelRecord(vehicleId = first, odometerKm = 100.0, fuelGrade = "95", pricePerLiter = 7.5, amountPaid = 30.0, liters = 4.0))
            dao.insertRecord(FuelRecord(vehicleId = first, odometerKm = 200.0, fuelGrade = "95", pricePerLiter = 7.5, amountPaid = 30.0, liters = 4.0))
            dao.insertRecord(FuelRecord(vehicleId = second, odometerKm = 300.0, fuelGrade = "92", pricePerLiter = 7.2, amountPaid = 36.0, liters = 5.0))
            assertEquals(2, dao.recordCount(first))
            dao.deleteVehicle(first)
            assertEquals(listOf("B"), dao.vehiclesOnce().map { it.name })
            assertEquals(0, dao.recordCount(first))
            assertEquals(1, dao.recordCount(second))
            dao.deleteVehicle(second)
            assertTrue(dao.vehiclesOnce().isEmpty())
            assertEquals(0, dao.recordCount(second))
        } finally {
            db.close()
        }
    }

    @Test fun allRequiredCasesUseRealRoomDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuelDatabase::class.java).build()
        try {
            val dao = db.dao()
            val vehicleId = dao.insertVehicle(Vehicle(name = "春风 150 AURA"))
            assertEquals("春风 150 AURA", dao.vehiclesOnce().single().name)

            suspend fun add(km: Double, price: Double, amount: Double, liters: Double): Long {
                val previous = dao.latestRecord(vehicleId)?.odometerKm
                assertNull(validateRecordInput(previous, km, price, amount, liters))
                return dao.insertRecord(FuelRecord(
                    vehicleId = vehicleId, odometerKm = km, fuelGrade = "95",
                    pricePerLiter = price, amountPaid = amount, liters = liters
                ))
            }

            add(10000.0, 7.50, 45.0, 6.0)
            var calculated = Consumption.calculate(dao.records(vehicleId).first())
            assertNull(calculated.single().litersPer100Km)

            val middleId = add(10200.0, 7.50, 60.0, 8.0)
            calculated = Consumption.calculate(dao.records(vehicleId).first())
            assertEquals(200.0, calculated[1].distanceKm!!, 0.001)
            assertEquals(4.0, calculated[1].litersPer100Km!!, 0.001)

            add(10450.0, 7.50, 63.75, 8.5)
            calculated = Consumption.calculate(dao.records(vehicleId).first())
            assertEquals(250.0, calculated[2].distanceKm!!, 0.001)
            assertEquals(3.4, calculated[2].litersPer100Km!!, 0.001)

            assertEquals(
                "当前里程不能低于上一条记录。",
                validateRecordInput(10450.0, 10400.0, 7.5, 60.0, 8.0)
            )

            dao.deleteRecord(middleId)
            calculated = Consumption.calculate(dao.records(vehicleId).first())
            assertEquals(450.0, calculated[1].distanceKm!!, 0.001)
            assertEquals(8.5 / 450.0 * 100, calculated[1].litersPer100Km!!, 0.001)
        } finally {
            db.close()
        }
    }
}
