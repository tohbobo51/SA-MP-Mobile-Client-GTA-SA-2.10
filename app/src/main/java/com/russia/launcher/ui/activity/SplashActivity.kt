package com.russia.launcher.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.russia.game.core.Samp
import com.russia.game.databinding.ActivitySplashBinding
import com.russia.launcher.storage.NativeStorage
import java.io.File

class SplashActivity : AppCompatActivity() {

    private var hasStarted = false
    private val REQUEST_ID = 228
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan tombol navigasi (Back, Home, Recents) secara permanen (Immersive Sticky)
        hideSystemUI()

        // Pastikan settings.ini tersimpan
        NativeStorage.ensureSettingsExist(this)

        // Jika diluncurkan dari Vice Side Launcher dengan parameter server/port, langsung buka Samp
        val serverHost = intent?.getStringExtra("server") ?: intent?.getStringExtra("ip")
        if (serverHost != null && serverHost.isNotEmpty()) {
            val sampIntent = Intent(this, Samp::class.java).apply {
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(sampIntent)
            finish()
            return
        }

        checkPermissions()

        // Delay singkat 800ms lalu otomatis masuk
        Handler(Looper.getMainLooper()).postDelayed({
            proceedToGame()
        }, 800)
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (ignored: Throwable) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_ID)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        proceedToGame()
    }

    private fun checkGameDataExists(): Boolean {
        val extDir = getExternalFilesDir(null) ?: return false

        // Auto-migrate jika user mengekstrak dengan folder "files" bersarang
        try {
            val nestedFiles = File(extDir, "files")
            if (nestedFiles.exists() && nestedFiles.isDirectory) {
                nestedFiles.listFiles()?.forEach { file ->
                    file.renameTo(File(extDir, file.name))
                }
            }
        } catch (ignored: Exception) {}

        // Cek data file (case insensitive)
        val dataDir = File(extDir, "data")
        val dataDirCap = File(extDir, "DATA")
        val texdbDir = File(extDir, "texdb")
        val texdbDirCap = File(extDir, "TEXDB")
        val modelsDir = File(extDir, "models")
        val animDir = File(extDir, "anim")

        return (dataDir.exists() && (dataDir.list()?.isNotEmpty() == true)) ||
               (dataDirCap.exists() && (dataDirCap.list()?.isNotEmpty() == true)) ||
               (texdbDir.exists() && (texdbDir.list()?.isNotEmpty() == true)) ||
               (texdbDirCap.exists() && (texdbDirCap.list()?.isNotEmpty() == true)) ||
               (modelsDir.exists() && (modelsDir.list()?.isNotEmpty() == true)) ||
               (animDir.exists() && (animDir.list()?.isNotEmpty() == true)) ||
               File(extDir, "data/gta.dat").exists() ||
               File(extDir, "anim/anim.img").exists()
    }

    private fun proceedToGame() {
        if (hasStarted) return
        hasStarted = true

        val hasData = checkGameDataExists()
        if (hasData) {
            // Data game ditemukan! Langsung luncurkan Samp ke server Vice Side
            val sampIntent = Intent(this, Samp::class.java).apply {
                putExtra("server", "142.132.203.47")
                putExtra("ip", "142.132.203.47")
                putExtra("port", "10125")
                val nick = NativeStorage.getClientProperty("name", this@SplashActivity) ?: "ViceSide_Player"
                putExtra("nick", nick)
                putExtra("name", nick)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(sampIntent)
            finish()
        } else {
            // Buka MainActivity launcher
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
