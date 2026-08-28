package com.fuellog.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fuellog.app.data.*
import com.fuellog.app.domain.Consumption
import com.fuellog.app.domain.RecordWithConsumption
import com.fuellog.app.domain.RecordEnergyType
import com.fuellog.app.domain.canChangeVehicleEnergyType
import com.fuellog.app.domain.validateEnergyCapacity
import com.fuellog.app.domain.isFutureLocalDate
import com.fuellog.app.domain.validateNewRecord
import com.fuellog.app.domain.validateRecordEdit
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AppState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeVehicle: Vehicle? = null,
    val records: List<RecordWithConsumption> = emptyList(),
    val recentPrices: Map<String, Double?> = emptyMap()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as FuelLogApplication).database.dao()
    private val prefs = application.getSharedPreferences("fuel_log", 0)
    private val activeId = MutableStateFlow(prefs.getLong("active_vehicle_id", -1))

    val state: StateFlow<AppState> = combine(dao.vehicles(), activeId) { vehicles, selectedId ->
        val active = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.firstOrNull()
        vehicles to active
    }.flatMapLatest { (vehicles, active) ->
        if (active == null) flowOf(AppState(vehicles))
        else {
            val recordTypes = RecordEnergyType.forVehicle(active.energyType)
            combine(
                dao.records(active.id),
                dao.latestRecordForGrade(active.id, recordTypes[0].storageValue),
                dao.latestRecordForGrade(active.id, recordTypes[1].storageValue)
            ) { records, latestFirst, latestSecond ->
            AppState(
                vehicles = vehicles,
                activeVehicle = active,
                records = Consumption.calculate(records),
                recentPrices = mapOf(
                    recordTypes[0].storageValue to latestFirst?.pricePerLiter,
                    recordTypes[1].storageValue to latestSecond?.pricePerLiter
                )
            )
            }.flowOn(Dispatchers.Default)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    fun selectVehicle(id: Long) {
        activeId.value = id
        prefs.edit().putLong("active_vehicle_id", id).apply()
    }

    suspend fun addVehicle(
        name: String,
        energyType: EnergyType = EnergyType.FUEL,
        energyCapacity: Double? = null
    ): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return "请输入车辆名称。"
        validateEnergyCapacity(energyCapacity)?.let { return it }
        selectVehicle(dao.insertVehicle(Vehicle(
            name = clean,
            energyType = energyType,
            energyCapacity = energyCapacity
        )))
        return null
    }

    suspend fun updateVehicle(
        vehicle: Vehicle,
        name: String,
        energyType: EnergyType,
        energyCapacity: Double?
    ): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return "请输入车辆名称。"
        validateEnergyCapacity(energyCapacity)?.let { return it }
        val current = dao.vehicleOnce(vehicle.id) ?: return "车辆不存在。"
        if (current.energyType != energyType && !canChangeVehicleEnergyType(dao.recordCount(vehicle.id))) {
            return "已有记录，车辆类型不可修改。"
        }
        if (dao.updateVehicleInfo(vehicle.id, clean, energyType, energyCapacity) != 1) {
            return "已有记录，车辆类型不可修改。"
        }
        return null
    }

    fun deleteVehicle(vehicle: Vehicle) = viewModelScope.launch {
        dao.deleteVehicle(vehicle.id)
        val next = nextActiveVehicleId(vehicle.id, activeId.value, dao.vehiclesOnce())
        if (next != activeId.value) selectVehicle(next)
    }

    suspend fun recordCount(vehicleId: Long): Int = dao.recordCount(vehicleId)

    suspend fun saveRecord(
        odometer: Double, grade: String, price: Double, amount: Double, liters: Double, timestamp: Long
    ): String? {
        val selectedVehicle = state.value.activeVehicle ?: return "请先添加车辆。"
        val vehicle = dao.vehicleOnce(selectedVehicle.id) ?: return "车辆不存在。"
        if (isFutureLocalDate(timestamp)) {
            return if (vehicle.energyType == EnergyType.FUEL) "加油日期不能晚于今天。" else "充电日期不能晚于今天。"
        }
        val record = FuelRecord(
            vehicleId = vehicle.id, odometerKm = odometer, fuelGrade = grade,
            pricePerLiter = price, amountPaid = amount, liters = liters, timestamp = timestamp
        )
        validateNewRecord(dao.recordsOnce(vehicle.id), record, vehicle.energyType)?.let { return it }
        dao.insertRecord(record)
        return null
    }

    fun deleteRecord(id: Long) = viewModelScope.launch { dao.deleteRecord(id) }

    fun validateRecordUpdate(updated: FuelRecord): String? {
        val vehicle = state.value.activeVehicle
            ?.takeIf { it.id == updated.vehicleId }
            ?: return "记录所属车辆已改变，请重新打开编辑页。"
        if (isFutureLocalDate(updated.timestamp)) {
            return if (vehicle.energyType == EnergyType.FUEL) "加油日期不能晚于今天。" else "充电日期不能晚于今天。"
        }
        return validateRecordEdit(state.value.records.map { it.record }, updated, vehicle.energyType)
    }

    suspend fun updateRecord(updated: FuelRecord): String? {
        val original = dao.recordOnce(updated.id) ?: return "记录不存在。"
        if (original.vehicleId != updated.vehicleId) return "不能更改记录所属车辆。"
        val vehicle = dao.vehicleOnce(original.vehicleId) ?: return "车辆不存在。"
        if (isFutureLocalDate(updated.timestamp)) {
            return if (vehicle.energyType == EnergyType.FUEL) "加油日期不能晚于今天。" else "充电日期不能晚于今天。"
        }
        validateRecordEdit(dao.recordsOnce(vehicle.id), updated, vehicle.energyType)?.let { return it }
        dao.updateRecord(updated)
        return null
    }
}

internal fun nextActiveVehicleId(deletedId: Long, activeId: Long, remaining: List<Vehicle>): Long =
    if (activeId != deletedId) activeId else remaining.firstOrNull()?.id ?: -1
