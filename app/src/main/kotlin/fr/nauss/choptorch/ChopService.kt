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
    private lateinit var notification: Notification
    @Volatile private var registered=false
    @Volatile private var stopping=false
    private var detector=ChopDetector()
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        worker=HandlerThread("choptorch-sensors").apply { start() }
        handler=Handler(worker.looper)
        sensors=getSystemService(SensorManager::class.java)
        torch=TorchController(this,handler) { message ->
            synchronized(this) { if(!stopping) LiveState.update { it.copy(torch=message) } }
        }
        val channel=NotificationChannel("gesture",getString(R.string.channel_name),NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null,null); enableVibration(false); setShowBadge(false)
            description=getString(R.string.channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,ChopService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        notification=Notification.Builder(this,"gesture").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title)).setContentText(getString(R.string.notification_text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,getString(R.string.stop),stop).build()).build()
    }
    private fun promote(): Boolean {
        return try { startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); true }
        catch(_: RuntimeException) {
            stopping=true
            Activation.setEnabled(this,false)
            LiveState.update { it.copy(running=false,error=R.string.error_start) }
            stopSelf()
            false
        }
    }
    @Synchronized override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A rapid stop/start may reuse the instance: every new FGS start must
        // satisfy Android's promotion deadline, not only the first onCreate.
        if(!promote()) return START_NOT_STICKY
        if(intent?.action==DISABLE_REQUEST) {
            // Ignore an old queued UI stop if fresh explicit consent followed it.
            if(Activation.enabled(this)) return START_STICKY
            stopping=true; stopSelf(); return START_NOT_STICKY
        }
        if(intent?.action==STOP) {
            Activation.setEnabled(this,false)
            stopping=true
            stopSelf(); return START_NOT_STICKY
        }
        if(intent?.action!=null && intent.action!=SENSITIVITY || intent?.action==SENSITIVITY && !registered) {
            // A settings/obsolete command must not create an idle sticky FGS.
            if(!registered) { stopping=true; stopSelf() }
            return if(registered) START_STICKY else START_NOT_STICKY
        }
        if(stopping || !Activation.eligible(this)) {
            val reason=Activation.blockedReason(this)
            if(reason!=0) Activation.setEnabled(this,false)
            stopping=true
            LiveState.update { it.copy(running=false,error=reason) }
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
        return START_STICKY
    }
    @Synchronized private fun startSensors() {
        if(stopping) return
        if(!Activation.enabled(this)) { stopping=true; stopSelf(); return }
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,false)
        if(sensor==null) { fail(R.string.error_sensor_missing); return }
        detector=ChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50))
        try { registered=sensors.registerListener(this,sensor,20_000,0,handler) }
        catch(_: SecurityException) { fail(R.string.error_sensor_access); return }
        if(!registered) { fail(R.string.error_sensor_access); return }
        val torchError=torch.open()
        if(torchError!=0) { fail(torchError); return }
        if(stopping || !Activation.enabled(this)) { stopSelf(); return }
        LiveState.update { it.copy(running=true,error=0) }
    }
    @Synchronized private fun fail(message: Int) {
        if(stopping) return
        stopping=true
        Activation.setEnabled(this,false)
        LiveState.update { it.copy(running=false,error=message) }; stopSelf()
    }
    @Synchronized override fun onSensorChanged(event: SensorEvent) {
        if(stopping || !registered || event.values.size<3) return
        val received=SystemClock.elapsedRealtimeNanos()
        val blocked=Activation.blockedReason(this)
        if(!Activation.enabled(this) || blocked!=0) {
            fail(blocked); return
        }
        val t=event.timestamp
        val x=event.values[0]; val y=event.values[1]; val z=event.values[2]
        val fresh=SampleFreshness.accepts(t,received)
        if(!fresh) {
            detector.cancel()
        } else if(detector.sample(t,x.toDouble(),y.toDouble(),z.toDouble())) {
            torch.toggle()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    @Synchronized override fun onDestroy() {
        stopping=true
        LiveState.update { it.copy(running=false) }
        // Queue cleanup after all worker commands; do not race with sensor callbacks.
        handler.post {
            stopping=true
            sensors.unregisterListener(this); registered=false
            detector.cancel(); torch.close()

            worker.quitSafely()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val STOP="fr.nauss.choptorch.STOP"
        const val DISABLE_REQUEST="fr.nauss.choptorch.DISABLE_REQUEST"
        const val SENSITIVITY="fr.nauss.choptorch.SENSITIVITY"
    }
}
