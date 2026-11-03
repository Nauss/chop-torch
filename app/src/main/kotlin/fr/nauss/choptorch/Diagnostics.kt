// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** No continuous raw logging. One opt-in in-memory capture, max 15 s / 1000 samples.
 * A new capture replaces the previous one. Process death discards all diagnostics.
 */
object Diagnostics {
    private val events = ArrayDeque<String>()
    private val samples = ArrayList<DiagnosticSample>()
    private var started = 0L
    private var deadline = 0L
    private var captureState = "Aucune capture"
    @Synchronized fun event(text: String) {
        if (events.size >= 64) events.removeFirst()
        events.addLast("${SystemClock.elapsedRealtime()} ms : $text")
    }
    @Synchronized fun begin() {
        samples.clear(); started=SystemClock.elapsedRealtimeNanos()
        deadline=started+15_000_000_000L; captureState="Capture en cours (15 s OU 1000 mesures, première limite atteinte)"
        event("Capture manuelle démarrée")
    }
    @Synchronized fun end() {
        if (deadline != 0L) captureState="Capture terminée : ${samples.size} mesures réelles" +
            if (samples.size >= 1000) " — limite 1000 mesures atteinte (peut durer moins de 15 s)" else ""
        deadline=0L
    }
    @Synchronized internal fun add(timestampNs: Long, receivedNs: Long, x: Float, y: Float, z: Float, fresh: Boolean, sensitivity: Int) {
        if (deadline == 0L) return
        if (SystemClock.elapsedRealtimeNanos() >= deadline || samples.size >= 1000) { end(); return }
        // Retain even stale/non-monotonic sensor times received during the
        // capture: otherwise precisely the rejected events would be hidden.
        samples.add(DiagnosticSample(timestampNs, receivedNs, x, y, z, fresh, sensitivity))
    }
    @Synchronized fun status(): String {
        if (deadline != 0L && SystemClock.elapsedRealtimeNanos() >= deadline) end()
        return "$captureState (${samples.size}/1000)"
    }
    @Synchronized fun clear() { deadline=0; samples.clear(); events.clear(); captureState="Diagnostics effacés" }
    fun inventory(context: Context): JSONArray {
        val manager=context.getSystemService(SensorManager::class.java)
        return JSONArray().apply {
            manager.getSensorList(Sensor.TYPE_ALL).forEach { s ->
                put(JSONObject().put("name",s.name).put("vendor",s.vendor).put("type",s.type)
                    .put("stringType",s.stringType).put("wakeUp",s.isWakeUpSensor)
                    .put("minDelayUs",s.minDelay).put("maxDelayUs",s.maxDelay)
                    .put("fifoReservedEvents",s.fifoReservedEventCount).put("fifoMaxEvents",s.fifoMaxEventCount)
                    .put("declaredPowerMa",s.power.toDouble()).put("resolution",s.resolution.toDouble())
                    .put("maxRange",s.maximumRange.toDouble()).put("reportingMode",s.reportingMode))
            }
        }
    }
    @Synchronized fun export(context: Context): String {
        status()
        val s=LiveState.state
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return JSONObject().put("format","choptorch-diagnostic-v2").put("sdk",android.os.Build.VERSION.SDK_INT)
            .put("versionName", info.versionName).put("versionCode", info.longVersionCode)
            .put("applicationId", context.packageName)
            .put("warning","Caractéristiques déclarées ≠ consommation mesurée. Veille NON validée. Détecteur expérimental.")
            .put("captureKind", "REAL_SENSOR_VALUES_ONLY_IF_MANUALLY_CAPTURED")
            .put("captureStartedElapsedRealtimeNs",started)
            .put("captureLimitMs",15000).put("captureLimitSamples",1000)
            .put("captureStatus",captureState).put("captureColumns",JSONArray(listOf("relative_ms","raw_x_m_s2","raw_y_m_s2","raw_z_m_s2","received_relative_ms","freshness_accepted","sensitivity")))
            .put("samples",JSONArray().apply { samples.forEach { put(JSONArray(it.columns(started))) } })
            .put("events",JSONArray(events.toList())).put("sensors",inventory(context))
            .put("running",s.running).put("sensorSelected",s.sensor).put("wakeUp",s.wakeUp)
            .put("sampleCountSinceStart",s.samples).put("lastPeriodMs",s.periodMs)
            .put("lastEventAgeMs",if(s.lastEventNs==0L) JSONObject.NULL else (SystemClock.elapsedRealtimeNanos()-s.lastEventNs)/1e6)
            .put("torchObserved",s.torch).put("gestures",s.gestures)
            .put("ignoringBatteryOptimizations",context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
            .put("sensitivity",context.getSharedPreferences("settings",0).getInt("sensitivity",50))
            .toString(2)
    }
}
