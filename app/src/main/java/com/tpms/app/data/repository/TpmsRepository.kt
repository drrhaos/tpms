package com.tpms.app.data.repository

import android.hardware.usb.UsbDevice
import com.tpms.app.data.db.SensorDao
import com.tpms.app.data.db.SensorReading
import com.tpms.app.data.usb.HidProtocol
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
import com.tpms.app.domain.model.PressureUnit
import com.tpms.app.domain.model.TireSensor
import com.tpms.app.domain.model.TpmsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TpmsRepository @Inject constructor(
    private val usbConnection: UsbConnection,
    private val hidProtocol: HidProtocol,
    private val sensorDao: SensorDao
) {
    private val _state = MutableStateFlow<TpmsState>(TpmsState.Disconnected)
    val state = _state.asStateFlow()

    private val _sensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val sensors = _sensors.asStateFlow()

    private val _alertTrigger = MutableSharedFlow<TireSensor>(extraBufferCapacity = 1)
    val alertTrigger = _alertTrigger.asSharedFlow()

    private var thresholds = AlertThresholds()
    private var onAlertCallback: ((TireSensor) -> Unit)? = null

    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    fun findDongle(): UsbDevice? = usbConnection.findDongle()

    fun openDongle(device: UsbDevice): Boolean = usbConnection.open(device)

    suspend fun readSensor(): TireSensor? {
        val rawFrame = usbConnection.read() ?: return null
        val frame = HidProtocol.RawFrame(rawFrame, System.currentTimeMillis())
        hidProtocol.logFrame(frame)

        val sensor = hidProtocol.parse(frame) { checkAlert(it) }
        if (sensor != null) {
            val updated = _sensors.value.toMutableMap()
            updated[sensor.id] = sensor
            _sensors.value = updated
            sensorDao.insert(sensor.toReading())
            if (sensor.isAlert) {
                onAlertCallback?.invoke(sensor)
                _alertTrigger.tryEmit(sensor)
            }
        }
        return sensor
    }

    fun updateState(state: TpmsState) {
        _state.value = state
    }

    fun updateThresholds(t: AlertThresholds) {
        thresholds = t
    }

    fun getThresholds(): AlertThresholds = thresholds

    private fun checkAlert(sensor: TireSensor): AlertType? {
        return when {
            sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
            sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
            sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
            sensor.batteryPercent < 10 -> AlertType.BATTERY_LOW
            else -> null
        }
    }

    fun closeUsb() {
        usbConnection.close()
        _sensors.value = emptyMap()
        _state.value = TpmsState.Disconnected
    }

    fun recentHistory(limit: Int = 20): Flow<List<SensorReading>> =
        sensorDao.recentReadings(limit)

    private fun TireSensor.toReading() = SensorReading(
        sensorId = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        alertType = alertType?.name,
        timestamp = timestamp
    )
}
