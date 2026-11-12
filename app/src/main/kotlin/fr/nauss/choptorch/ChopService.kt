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
    private lateinit var detector: PlaneLinearChopDetector
    private var proximity=ProximityGate(Double.NaN)
    private val policy=PolicyGate({ Activation.enabled(this) }, { Activation.blockedReason(this) })
    private val feedbackWindow=FeedbackWindow()
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        detector=PlaneLinearChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50),true)
        worker=HandlerThread("choptorch-sensors").apply { start() }
        handler=Handler(worker.looper)
        sensors=getSystemService(SensorManager::class.java)
        val haptics=TorchHaptics(this)
        torch=TorchController(this,handler,onConfirmed={
            synchronized(this) {
                if(!stopping && haptics.confirm()) {
                    feedbackWindow.start(SystemClock.elapsedRealtimeNanos())
                    detector.cancel()
                }
            }
        }) { message ->
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
            if(!checkPolicy(force=true)) return@post
            when(intent?.action) {
                SENSITIVITY -> detector=PlaneLinearChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50),true)
                null -> if(!registered) startSensors()
                // Unknown/removed commands do not start sensors or toggle the torch.
            }
        }
        return START_STICKY
    }
    @Synchronized private fun startSensors() {
        if(stopping) return
        if(!Activation.enabled(this)) { stopping=true; stopSelf(); return }
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION,true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION,false)
        if(sensor==null) { fail(R.string.error_sensor_missing); return }
        val contextSensor=sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY,true)
            ?: sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY,false)
        if(contextSensor==null) { fail(R.string.error_proximity); return }
        proximity=ProximityGate(contextSensor.maximumRange.toDouble())
        if(!proximity.available) { fail(R.string.error_proximity); return }
        detector=PlaneLinearChopDetector(getSharedPreferences("settings",0).getInt("sensitivity",50),true)
        val contextRegistered=try {
            sensors.registerListener(this,contextSensor,SensorManager.SENSOR_DELAY_NORMAL,0,handler)
        } catch(_: RuntimeException) { false }
        if(!contextRegistered) { fail(R.string.error_proximity); return }
        try { registered=sensors.registerListener(this,sensor,20_000,0,handler) }
        catch(_: RuntimeException) { fail(R.string.error_sensor_access); return }
        if(!registered) { fail(R.string.error_sensor_access); return }
        val torchError=torch.open()
        if(torchError!=0) { fail(torchError); return }
        if(stopping || !checkPolicy(force=true)) { stopSelf(); return }
        LiveState.update { it.copy(running=true,error=0,context=R.string.context_checking) }
    }
    @Synchronized private fun fail(message: Int) {
        if(stopping) return
        stopping=true
        sensors.unregisterListener(this); registered=false
        detector.cancel(); proximity.reset()
        Activation.setEnabled(this,false)
        LiveState.update { it.copy(running=false,error=message,context=0) }; stopSelf()
    }
    private fun checkPolicy(force: Boolean=false): Boolean {
        if(policy.allows(SystemClock.elapsedRealtimeNanos(),force)) return true
        fail(policy.blockedReason)
        return false
    }
    @Synchronized override fun onSensorChanged(event: SensorEvent) {
        if(stopping || !registered) return
        if(!checkPolicy()) return
        // Proximity is an on-change, one-value sensor. Never apply the vector guard to it.
        if(event.sensor.type==Sensor.TYPE_PROXIMITY) {
            if(proximity.sample(event.timestamp,event.values.firstOrNull()?.toDouble() ?: Double.NaN)) {
                detector.cancel()
                val message=when(proximity.state) {
                    ProximityGate.State.UNKNOWN -> R.string.context_checking
                    ProximityGate.State.NEAR -> R.string.context_covered
                    ProximityGate.State.FAR -> 0
                }
                LiveState.update { it.copy(context=message) }
            }
            return
        }
        if(event.sensor.type!=Sensor.TYPE_LINEAR_ACCELERATION) return
        if(event.values.size<3 || !proximity.allowsGesture) { detector.cancel(); return }
        val received=SystemClock.elapsedRealtimeNanos()
        val t=event.timestamp
        val x=event.values[0]; val y=event.values[1]; val z=event.values[2]
        val fresh=SampleFreshness.accepts(t,received)
        if(!fresh) {
            detector.cancel()
        } else if(feedbackWindow.sample(detector,t,x.toDouble(),y.toDouble(),z.toDouble())) {
            // Cached policy is never authority for a command, even within its one-second TTL.
            if(!stopping && proximity.allowsGesture && checkPolicy(force=true)) torch.toggle()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    @Synchronized override fun onDestroy() {
        stopping=true
        LiveState.update { it.copy(running=false,context=0) }
        // Queue cleanup after all worker commands; do not race with sensor callbacks.
        handler.post {
            stopping=true
            sensors.unregisterListener(this); registered=false
            detector.cancel(); proximity.reset(); torch.close()

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
