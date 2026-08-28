package com.fuellog.app

import android.app.Application
import com.fuellog.app.data.FuelDatabase

class FuelLogApplication : Application() {
    val database by lazy { FuelDatabase.create(this) }
}
