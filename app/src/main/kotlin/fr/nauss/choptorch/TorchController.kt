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
        report("Commande sans confirmation — réessayer manuellement")
    }
    private val callback=object: CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if(!registered || cameraId!=id) return
            observed=enabled; pending=false; handler.removeCallbacks(timeout)
            if(!enabled) owned=false
            report(if(enabled) "Allumée (callback)" else "Éteinte (callback)")
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if(!registered || cameraId!=id) return
            observed=null; pending=false; owned=false; handler.removeCallbacks(timeout)
            report("Indisponible : caméra occupée ou ressource retirée")
        }
    }
    private fun report(text: String) {
        LiveState.update { it.copy(torch=text) }; Diagnostics.event("Torche : $text")
    }
    fun open() {
        try {
            val cameras=manager.cameraIdList.toList()
            id=cameras.firstOrNull {
                val c=manager.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true &&
                    c.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK
            } ?: cameras.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
            if(id==null) { report("Aucune caméra avec flash"); return }
            manager.registerTorchCallback(callback,handler); registered=true
        } catch(e: CameraAccessException) { report("Accès caméra indisponible (${e.reason})") }
          catch(e: SecurityException) { report("Accès refusé par le système — aucune permission superflue demandée") }
          catch(e: IllegalArgumentException) { report("Caméra retirée") }
    }
    fun toggle() {
        val camera=id ?: return
        val current=observed
        if(current==null || pending) { Diagnostics.event("Commande ignorée : état torche inconnu/indisponible ou attente callback"); return }
        try {
            pending=true
            manager.setTorchMode(camera,!current)
            owned=!current
            handler.postDelayed(timeout,2000)
            // Do not change observed state until TorchCallback.
        } catch(e: CameraAccessException) { pending=false; report("Commande refusée : ressource occupée/indisponible (${e.reason})") }
          catch(e: SecurityException) { pending=false; observed=null; report("Accès refusé/révoqué") }
          catch(e: IllegalArgumentException) { pending=false; observed=null; report("Caméra/flash absent") }
    }
    fun close() {
        handler.removeCallbacks(timeout)
        if(owned) {
            try { id?.let { manager.setTorchMode(it,false) } }
            catch(e: CameraAccessException) { Diagnostics.event("Extinction arrêt non confirmée (${e.reason})") }
            catch(e: SecurityException) { Diagnostics.event("Extinction arrêt refusée") }
            catch(e: IllegalArgumentException) { Diagnostics.event("Flash retiré à l’arrêt") }
        }
        if(registered) manager.unregisterTorchCallback(callback)
        registered=false; observed=null; pending=false; owned=false
        LiveState.update { it.copy(torch="Non suivi : service arrêté (extinction demandée si allumée par ChopTorch)") }
    }
}
