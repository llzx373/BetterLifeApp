// 传感器路径的今日步数：监听 TYPE_STEP_COUNTER（开机累计值），
// 用 DataStore 记住每日零点基线换算成今日步数（换算逻辑见 StepsBaseline.kt）。
//
// 这是 Health Connect 不可用时的兜底，只在门面判定 HC 不可用时才会被订阅。
package com.betterlife.app.data.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.stepsDataStore by preferencesDataStore(name = "steps")

class SensorStepsRepository(private val context: Context) : SensorSteps {

    private object Keys {
        val BASELINE_DATE = stringPreferencesKey("baseline_date")
        val BASELINE_VALUE = longPreferencesKey("baseline_value")
        val LAST_COUNTER = longPreferencesKey("last_counter")
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override val available: Boolean get() = stepSensor != null

    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    override fun todayStepsFlow(): Flow<Long> = counterEvents().map { counter ->
        val prefs = context.stepsDataStore.data.first()
        val prev = if (prefs.contains(Keys.BASELINE_DATE)) {
            StepsBaseline(
                date = prefs[Keys.BASELINE_DATE].orEmpty(),
                baseline = prefs[Keys.BASELINE_VALUE] ?: 0L,
                lastCounter = prefs[Keys.LAST_COUNTER] ?: 0L,
            )
        } else {
            null
        }
        val (next, steps) = computeTodaySteps(prev, counter, LocalDate.now().toString())
        context.stepsDataStore.edit {
            it[Keys.BASELINE_DATE] = next.date
            it[Keys.BASELINE_VALUE] = next.baseline
            it[Keys.LAST_COUNTER] = next.lastCounter
        }
        steps
    }

    /** 传感器原始读数流（开机累计值）；订阅即注册监听，取消即注销 */
    private fun counterEvents(): Flow<Long> = callbackFlow {
        val sensor = stepSensor ?: run {
            close()
            return@callbackFlow
        }
        val manager = sensorManager ?: run {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }
}
