// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

/** Single native Views screen. Diagnostic copy is intentionally French-only in lot A. */
@SuppressLint("SetTextI18n")
class MainActivity: Activity() {
    private lateinit var activation: Switch
    private lateinit var stateText: TextView
    private lateinit var batteryText: TextView
    private lateinit var captureText: TextView
    private lateinit var sensitivityText: TextView
    private lateinit var captureButton: Button
    private lateinit var torchButton: Button
    private var rendering=false
    private val observer: () -> Unit = { render() }
    private val main=Handler(Looper.getMainLooper())
    private val refresh=object: Runnable {
        override fun run() { render(); main.postDelayed(this,1000) }
    }
    private var pendingExport: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scale=resources.displayMetrics.density
        val pad=(20*scale).toInt()
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad) }
        val scroll=ScrollView(this).apply { addView(content) }
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets
        }
        setContentView(scroll)
        fun text(value: String, size: Float=16f) = TextView(this).apply {
            text=value; textSize=size; setPadding(0,pad/2,0,pad/2); content.addView(this)
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text=label; content.addView(this,ViewGroup.LayoutParams(-1,-2)); setOnClickListener { action() }
        }
        text("ChopTorch",28f)
        text("APK DIAGNOSTIC PROVISOIRE · Android 17",14f)
        text("Double chop expérimental. Veille et autonomie NON validées. Tenez fermement le téléphone : aucun lancer ni mouvement violent.")
        activation=Switch(this).apply {
            text="Activer la détection"; content.addView(this)
            setOnCheckedChangeListener { _,checked ->
                if(!rendering) {
                    if(checked) {
                        render()
                        AlertDialog.Builder(this@MainActivity).setTitle("Activer le service ?")
                            .setMessage("Accéléromètre actif en arrière-plan et notification silencieuse. Aucun micro, réseau ou flux caméra. La consommation reste à mesurer. Vous pouvez arrêter depuis cet écran ou la notification.")
                            .setPositiveButton("Activer") { _,_ -> requestActivation() }
                            .setNegativeButton("Annuler") { _,_ -> render() }.show()
                    } else {
                        getSharedPreferences("settings",0).edit().putBoolean("enabled",false).apply()
                        stopService(Intent(this@MainActivity,ChopService::class.java))
                    }
                }
            }
        }
        stateText=text("")
        val prefs=getSharedPreferences("settings",0)
        sensitivityText=text("")
        val slider=SeekBar(this).apply {
            max=100; progress=prefs.getInt("sensitivity",50).coerceIn(0,100)
            contentDescription="Sensibilité du double chop"
            content.addView(this)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    sensitivityText.text="Sensibilité expérimentale : $progress / 100"
                    if(fromUser) {
                        prefs.edit().putInt("sensitivity",progress).apply()
                        if(LiveState.state.running) command(ChopService.SENSITIVITY)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        sensitivityText.text="Sensibilité expérimentale : ${slider.progress} / 100"
        batteryText=text("")
        button("Réglages d’optimisation batterie") {
            AlertDialog.Builder(this).setTitle("Exemption facultative")
                .setMessage("Dans les réglages Android, choisissez ChopTorch diagnostic si vous souhaitez l’exempter. Cela ne prouve ni la réception en veille ni une faible consommation. Ne changez pas l’économie globale.")
                .setPositiveButton("Ouvrir les réglages") { _,_ ->
                    try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    catch(e: ActivityNotFoundException) { toast("Réglage indisponible") }
                }.setNegativeButton("Annuler",null).show()
        }
        text("Diagnostic provisoire",22f)
        torchButton=button("Tester la torche (commuter)") { command(ChopService.TEST_TORCH) }
        captureText=text("")
        captureButton=button("Capturer (max. 15 s / 1000 mesures)") {
            AlertDialog.Builder(this).setTitle("Capture volontaire")
                .setMessage("Remplace la capture précédente. Arrêt à la première limite : 15 secondes OU 1000 mesures (environ 8 secondes sur le Pixel observé). Seulement en mémoire. Faites deux doubles chops confortables, sans forcer, espacés de quelques secondes ; arrêtez si inconfortable. Export uniquement à votre demande. Les traces peuvent révéler vos mouvements ; vérifiez le fichier avant partage.")
                .setPositiveButton("Démarrer") { _,_ -> command(ChopService.CAPTURE) }
                .setNegativeButton("Annuler",null).show()
        }
        button("Exporter inventaire et diagnostic JSON") {
            pendingExport=Diagnostics.export(this)
            try {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type="application/json"
                    putExtra(Intent.EXTRA_TITLE,"choptorch-diagnostic.json")
                },EXPORT)
            } catch(e: ActivityNotFoundException) { pendingExport=null; toast("Sélecteur de document indisponible") }
        }
        button("Effacer les diagnostics en mémoire") { Diagnostics.clear(); render() }
        text("Wake-up préféré si disponible ; sans lui, réception fiable écran éteint non assurée. Même avec wake-up, essai Pixel nécessaire. Aucune mesure de batterie déduite de la puissance déclarée des capteurs.")
        text("Lot A : arrêt système, redémarrage, mise à jour ou mort du processus ⇒ réactivation manuelle. Sensibilité conservée ; aucune relance cachée. Aucune signature de production ni publication F-Droid à ce stade.",14f)
    }
    private fun command(action: String) {
        if(!LiveState.state.running) { toast("Activez d’abord le service"); return }
        try { startService(Intent(this,ChopService::class.java).setAction(action)) }
        catch(e: RuntimeException) { toast("Commande refusée par Android") }
    }
    private fun requestActivation() {
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),NOTIFICATION)
        } else startDetection()
    }
    private fun startDetection() {
        if(!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            toast("Autorisez les notifications pour rendre ce service visible"); render(); return
        }
        try { startForegroundService(Intent(this,ChopService::class.java)) }
        catch(e: RuntimeException) {
            LiveState.update { it.copy(running=false,message="Démarrage refusé : ${e.javaClass.simpleName}") }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==NOTIFICATION && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startDetection()
        else { toast("Activation annulée : notification non autorisée"); render() }
    }
    @Deprecated("Native Activity document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=EXPORT) return
        val payload=pendingExport; pendingExport=null
        if(resultCode!=RESULT_OK) return
        if(payload==null) { toast("Export annulé après recréation de l’écran — recommencez"); return }
        val uri=data?.data ?: return
        Thread {
            try {
                val stream=contentResolver.openOutputStream(uri,"wt") ?: error("Document non accessible")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                runOnUiThread { toast("Diagnostic exporté") }
            } catch(e: Exception) { runOnUiThread { toast("Échec de l’export (accès ou espace indisponible)") } }
        }.start()
    }
    override fun onResume() {
        super.onResume()
        if(LiveState.state.running && !getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            stopService(Intent(this,ChopService::class.java))
            toast("Notifications désactivées : arrêt du diagnostic à la réouverture")
        }
        LiveState.observe(observer); main.post(refresh)
    }
    override fun onPause() { main.removeCallbacks(refresh); LiveState.remove(observer); super.onPause() }
    private fun render() {
        if(!::activation.isInitialized) return
        val s=LiveState.state
        rendering=true; activation.isChecked=s.running; rendering=false
        val age=if(s.lastEventNs==0L) "aucune" else String.format(Locale.FRANCE,"%.1f s",(SystemClock.elapsedRealtimeNanos()-s.lastEventNs)/1e9)
        stateText.text="Service : ${s.message}\nTorche : ${s.torch}\nCapteur : ${s.sensor}\nWake-up : ${s.wakeUp} (veille non validée)\nMesures : ${s.samples}, dernière : $age\nDernier intervalle : ${String.format(Locale.FRANCE,"%.1f",s.periodMs)} ms\nGestes expérimentaux : ${s.gestures}"
        batteryText.text="Exemption optimisation batterie : " + if(getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) "accordée (observée)" else "non accordée"
        captureText.text=Diagnostics.status()
        captureButton.isEnabled=s.running; torchButton.isEnabled=s.running
    }
    private fun toast(text: String) { Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
    companion object { private const val NOTIFICATION=1; private const val EXPORT=2 }
}
