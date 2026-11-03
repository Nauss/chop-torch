// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler

/** Uses only torch APIs, never opens a camera. Serialized on the sensor worker. */
class TorchController(context: Context, private val handler: Handler) {
    private val manager=context.getSystemService(CameraManager::class.java)
    private var id: String? = null
    private var observed: Boolean? = null
    private var pending = false
    private var owned = false
    private var registered = false
    private val timeout=Runnable {
        pending=false
        report(R.string.torch_unconfirmed)
    }
    private val callback=object: CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if(!registered || cameraId!=id) return
            observed=enabled; pending=false; handler.removeCallbacks(timeout)
            if(!enabled) owned=false
            report(if(enabled) R.string.torch_on else R.string.torch_off)
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if(!registered || cameraId!=id) return
            observed=null; pending=false; owned=false; handler.removeCallbacks(timeout)
            report(R.string.torch_unavailable)
        }
    }
    private fun report(message: Int) { LiveState.update { it.copy(torch=message) } }
    fun open() {
        try {
            val cameras=manager.cameraIdList.toList()
            id=cameras.firstOrNull {
                val c=manager.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true &&
                    c.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK
            } ?: cameras.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
            if(id==null) { report(R.string.torch_missing); return }
            manager.registerTorchCallback(callback,handler); registered=true
        } catch(_: CameraAccessException) { report(R.string.torch_unavailable) }
          catch(_: SecurityException) { report(R.string.torch_denied) }
          catch(_: IllegalArgumentException) { report(R.string.torch_missing) }
    }
    fun toggle() {
        val camera=id ?: return
        val current=observed
        if(current==null || pending) return
        try {
            pending=true
            manager.setTorchMode(camera,!current)
            owned=!current
            handler.postDelayed(timeout,2000)
            // Do not change observed state until TorchCallback.
        } catch(_: CameraAccessException) { pending=false; report(R.string.torch_unavailable) }
          catch(_: SecurityException) { pending=false; observed=null; report(R.string.torch_denied) }
          catch(_: IllegalArgumentException) { pending=false; observed=null; report(R.string.torch_missing) }
    }
    fun close() {
        handler.removeCallbacks(timeout)
        var result=R.string.torch_unknown
        if(owned) {
            try { id?.let { manager.setTorchMode(it,false) } }
            catch(_: CameraAccessException) { result=R.string.torch_unconfirmed }
            catch(_: SecurityException) { result=R.string.torch_denied }
            catch(_: IllegalArgumentException) { result=R.string.torch_missing }
        }
        if(registered) manager.unregisterTorchCallback(callback)
        registered=false; observed=null; pending=false; owned=false
        report(result)
    }
}
