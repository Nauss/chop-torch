// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*

class ChopService: Service(), SensorEventListener {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var sensors: SensorManager
    private lateinit var torch: TorchController
    private var registered=false
    private var stopping=false
    private var detector=ChopDetector()
    private var count=0L
    private var previous=0L
    private var lastPublish=0L
    private var gestures=0L
    private val finishCapture=Runnable { Diagnostics.end(); publishMessage("Capture terminée (service actif)") }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        worker=HandlerThread("choptorch-sensors").apply { start() }
        handler=Handler(worker.looper)
        sensors=getSystemService(SensorManager::class.java)
        torch=TorchController(this,handler)
        val channel=NotificationChannel("gesture","Détection du double chop",NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null,null); enableVibration(false); setShowBadge(false)
            description="Service explicitement activé, sans son ni maintien permanent du processeur éveillé."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,ChopService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"gesture").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("ChopTorch diagnostic actif").setContentText("Double chop expérimental — veille non validée")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Arrêter",stop).build()).build()
        try { startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        catch(e: RuntimeException) {
            stopping=true
            LiveState.update { it.copy(running=false,message="Démarrage foreground refusé : ${e.javaClass.simpleName}") }
            stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==STOP) {
            getSharedPreferences("settings",0).edit().putBoolean("enabled",false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        handler.post {
            if(stopping) return@post
            when(intent?.action) {
                CAPTURE -> if(registered) {
                    Diagnostics.begin(); handler.removeCallbacks(finishCapture); handler.postDelayed(finishCapture,15_000)
                    publishMessage("Capture volontaire : max. 15 s / 1000 mesures")
                }
                TEST_TORCH -> if(registered) torch.toggle()
                SENSITIVITY -> detector=ChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50))
                else -> if(!registered) startSensors()
            }
        }
        // Deliberately manual restart in lot A: no hidden restart after stop/boot.
        return START_NOT_STICKY
    }
    private fun startSensors() {
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,false)
        if(sensor==null) { fail("Aucun accéléromètre"); return }
        detector=ChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50))
        try { registered=sensors.registerListener(this,sensor,20_000,0,handler) }
        catch(e: SecurityException) { fail("Accès capteur refusé/révoqué"); return }
        if(!registered) { fail("Inscription capteur refusée"); return }
        torch.open()
        getSharedPreferences("settings",0).edit().putBoolean("enabled",true).apply()
        LiveState.update { it.copy(running=true,message="Actif — veille à tester",sensor=sensor.name,wakeUp=sensor.isWakeUpSensor,samples=0,gestures=0) }
        Diagnostics.event("Service actif, wake-up=${sensor.isWakeUpSensor}, période demandée 20000 µs, latence lot 0")
    }
    private fun publishMessage(text: String) { LiveState.update { it.copy(message=text) } }
    private fun fail(text: String) {
        Diagnostics.event(text); LiveState.update { it.copy(running=false,message=text) }; stopSelf()
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(stopping || !registered || event.values.size<3) return
        val t=event.timestamp
        val received=SystemClock.elapsedRealtimeNanos()
        val x=event.values[0]; val y=event.values[1]; val z=event.values[2]
        val fresh=SampleFreshness.accepts(t,received)
        Diagnostics.add(t,received,x,y,z,fresh,detector.sensitivity)
        count++
        if(!fresh) {
            detector.cancel()
        } else if(detector.sample(t,x.toDouble(),y.toDouble(),z.toDouble())) {
            gestures++; Diagnostics.event("Double chop expérimental reconnu"); torch.toggle()
        }
        val period=if(previous==0L) 0.0 else (t-previous)/1e6
        previous=t
        if(t-lastPublish>=200_000_000L) {
            lastPublish=t
            LiveState.update { it.copy(samples=count,lastEventNs=t,periodMs=period,gestures=gestures) }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        // Queue cleanup after all worker commands; do not race with sensor callbacks.
        handler.post {
            stopping=true
            handler.removeCallbacks(finishCapture)
            sensors.unregisterListener(this); registered=false
            detector.cancel(); Diagnostics.end(); torch.close()
            LiveState.update { it.copy(running=false,message=if(it.running) "Arrêté — réactivation manuelle" else it.message) }
            Diagnostics.event("Service arrêté, capteur libéré")
            worker.quitSafely()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val STOP="fr.nauss.choptorch.STOP"
        const val CAPTURE="fr.nauss.choptorch.CAPTURE"
        const val TEST_TORCH="fr.nauss.choptorch.TEST_TORCH"
        const val SENSITIVITY="fr.nauss.choptorch.SENSITIVITY"
    }
}
