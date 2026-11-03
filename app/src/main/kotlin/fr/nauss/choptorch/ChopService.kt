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
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        worker=HandlerThread("choptorch-sensors").apply { start() }
        handler=Handler(worker.looper)
        sensors=getSystemService(SensorManager::class.java)
        torch=TorchController(this,handler)
        val channel=NotificationChannel("gesture",getString(R.string.channel_name),NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null,null); enableVibration(false); setShowBadge(false)
            description=getString(R.string.channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,ChopService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"gesture").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title)).setContentText(getString(R.string.notification_text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,getString(R.string.stop),stop).build()).build()
        try { startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        catch(_: RuntimeException) {
            stopping=true
            LiveState.update { it.copy(running=false,error=R.string.error_start) }
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
                SENSITIVITY -> detector=ChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50))
                null -> if(!registered) startSensors()
                // Unknown/removed commands do not start sensors or toggle the torch.
            }
        }
        // Lifecycle recovery is intentionally reserved for P2.
        return START_NOT_STICKY
    }
    private fun startSensors() {
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,false)
        if(sensor==null) { fail(R.string.error_sensor_missing); return }
        detector=ChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50))
        try { registered=sensors.registerListener(this,sensor,20_000,0,handler) }
        catch(_: SecurityException) { fail(R.string.error_sensor_access); return }
        if(!registered) { fail(R.string.error_sensor_access); return }
        torch.open()
        getSharedPreferences("settings",0).edit().putBoolean("enabled",true).apply()
        LiveState.update { it.copy(running=true,error=0) }
    }
    private fun fail(message: Int) {
        LiveState.update { it.copy(running=false,error=message) }; stopSelf()
    }
    override fun onSensorChanged(event: SensorEvent) {
        if(stopping || !registered || event.values.size<3) return
        val t=event.timestamp
        val received=SystemClock.elapsedRealtimeNanos()
        val x=event.values[0]; val y=event.values[1]; val z=event.values[2]
        val fresh=SampleFreshness.accepts(t,received)
        if(!fresh) {
            detector.cancel()
        } else if(detector.sample(t,x.toDouble(),y.toDouble(),z.toDouble())) {
            torch.toggle()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        // Queue cleanup after all worker commands; do not race with sensor callbacks.
        handler.post {
            stopping=true
            sensors.unregisterListener(this); registered=false
            detector.cancel(); torch.close()
            LiveState.update { it.copy(running=false) }
            worker.quitSafely()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val STOP="fr.nauss.choptorch.STOP"
        const val SENSITIVITY="fr.nauss.choptorch.SENSITIVITY"
    }
}
