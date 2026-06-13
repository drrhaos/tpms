package com.tpms.app.data.repository

import android.hardware.usb.UsbDevice
import com.tpms.app.data.db.SensorDao
import com.tpms.app.data.db.SensorReading
import com.tpms.app.data.settings.SettingsStore
import com.tpms.app.data.usb.HidProtocol
import com.tpms.app.data.usb.UsbConnection
import com.tpms.app.domain.model.AlertThresholds
import com.tpms.app.domain.model.AlertType
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
    private val sensorDao: SensorDao,
    private val settingsStore: SettingsStore
) {
    private val _state = MutableStateFlow<TpmsState>(TpmsState.Disconnected)
    val state = _state.asStateFlow()

    private val _sensors = MutableStateFlow<Map<String, TireSensor>>(emptyMap())
    val sensors = _sensors.asStateFlow()

    private val _alertTrigger = MutableSharedFlow<TireSensor>(extraBufferCapacity = 4)
    val alertTrigger = _alertTrigger.asSharedFlow()

    private val lastSeen = mutableMapOf<String, Long>()
    private var onAlertCallback: ((TireSensor) -> Unit)? = null

    fun onAlert(callback: (TireSensor) -> Unit) {
        onAlertCallback = callback
    }

    fun findDongle(): UsbDevice? = usbConnection.findDongle()

    fun hasUsbPermission(device: UsbDevice): Boolean = usbConnection.hasPermission(device)

    fun openDongle(device: UsbDevice): Boolean = usbConnection.open(device)

    suspend fun readSensor(): TireSensor? {
        val rawFrame = usbConnection.read() ?: return null
        val frame = HidProtocol.RawFrame(rawFrame, System.currentTimeMillis())
        hidProtocol.logFrame(frame)

        val sensor = hidProtocol.parse(frame) { checkAlert(it) }
        if (sensor != null) {
            lastSeen[sensor.id] = sensor.timestamp
            val updated = _sensors.value.toMutableMap()
            updated[sensor.id] = sensor
            _sensors.value = updated
            sensorDao.insert(sensor.toReading())
            if (sensor.isAlert) {
                emitAlert(sensor)
            }
            refreshConnectionState(sensor.timestamp)
        }
        return sensor
    }

    fun checkSensorTimeouts(now: Long = System.currentTimeMillis()): List<TireSensor> {
        val lostSensors = mutableListOf<TireSensor>()
        val updated = _sensors.value.toMutableMap()
        var changed = false

        for ((id, sensor) in _sensors.value) {
            val seen = lastSeen[id] ?: continue
            if (now - seen > SENSOR_TIMEOUT_MS && sensor.alertType != AlertType.SENSOR_LOST) {
                val lost = sensor.copy(alertType = AlertType.SENSOR_LOST)
                updated[id] = lost
                lostSensors.add(lost)
                changed = true
                emitAlert(lost)
            }
        }

        if (changed) {
            _sensors.value = updated
            refreshConnectionState(now)
        }
        return lostSensors
    }

    fun updateState(state: TpmsState) {
        _state.value = state
    }

    fun getThresholds(): AlertThresholds = settingsStore.thresholds.value

    fun closeUsb() {
        usbConnection.close()
        lastSeen.clear()
        _sensors.value = emptyMap()
        _state.value = TpmsState.Disconnected
    }

    fun recentHistory(limit: Int = 20): Flow<List<SensorReading>> =
        sensorDao.recentReadings(limit)

    private fun refreshConnectionState(timestamp: Long) {
        val sensorList = _sensors.value.values.toList()
        if (sensorList.isEmpty()) return

        val alerting = sensorList.firstOrNull { it.isAlert }
        _state.value = if (alerting != null) {
            TpmsState.Alert(
                sensor = alerting,
                type = alerting.alertType!!,
                previousState = TpmsState.Connected(sensorList, timestamp)
            )
        } else {
            TpmsState.Connected(sensorList, timestamp)
        }
    }

    private fun checkAlert(sensor: TireSensor): AlertType? {
        val thresholds = settingsStore.thresholds.value
        return when {
            sensor.pressureKpa < thresholds.lowPressureKpa -> AlertType.LOW_PRESSURE
            sensor.pressureKpa > thresholds.highPressureKpa -> AlertType.HIGH_PRESSURE
            sensor.temperatureCelsius > thresholds.highTempCelsius -> AlertType.HIGH_TEMP
            sensor.batteryPercent < 10 -> AlertType.BATTERY_LOW
            else -> null
        }
    }

    private fun emitAlert(sensor: TireSensor) {
        onAlertCallback?.invoke(sensor)
        _alertTrigger.tryEmit(sensor)
    }

    private fun TireSensor.toReading() = SensorReading(
        sensorId = id,
        pressureKpa = pressureKpa,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        alertType = alertType?.name,
        timestamp = timestamp
    )

    companion object {
        private const val SENSOR_TIMEOUT_MS = 60_000L
    }
}
