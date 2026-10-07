package com.russia.launcher.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.GsonBuilder
import com.russia.game.core.Samp
import com.russia.game.databinding.ActivitySplashBinding
import com.russia.launcher.NetworkService
import com.russia.launcher.async.dto.response.GameFileInfoDto
import com.russia.launcher.async.dto.response.MonitoringDataLoaderListener
import com.russia.launcher.async.dto.response.ServersList
import com.russia.launcher.async.task.CacheChecker
import com.russia.launcher.config.Config
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class SplashActivity : AppCompatActivity() {

    private var permissionsGranded = false
    private var apkVersionChecked = true
    private var monitoringDataLoaded = true
    private var filesListLoaded = true
    private var animationEnded = false
    private var hasStarted = false

    private val REQUEST_ID = 228
    private val permissionList = arrayOf(
        android.Manifest.permission.RECORD_AUDIO
    )

    private var networkService: NetworkService
    private lateinit var binding: ActivitySplashBinding

    init {
        val gson = GsonBuilder().setLenient().create()
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(Config.LIVE_RUSSIA_RESOURCE_SERVER_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
        networkService = retrofit.create(NetworkService::class.java)
    }

    private val isOnline: Boolean
        get() {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.activeNetworkInfo != null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        } catch (ignored: Exception) {}

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan tombol navigasi (Back, Home, Recents) secara permanen (Immersive Sticky)
        hideSystemUI()

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

        binding.lottieLogo.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animationEnded = true
                startIfReady()
            }
        })

        // Safety fallback: Jangan biarkan stuck di logo jika animasi loop atau server lambat
        Handler(Looper.getMainLooper()).postDelayed({
            animationEnded = true
            startIfReady()
        }, 1200)

        // Muat server monitoring di background tanpa memblokir
        try {
            ServersList.load(
                this,
                networkService,
                object : MonitoringDataLoaderListener {
                    override fun monitoringDataLoadedSuccess() {
                        monitoringDataLoaded = true
                        startIfReady()
                    }
                }
            )
        } catch (ignored: Exception) {
            monitoringDataLoaded = true
        }

        loadFilesList()
        checkPermissions()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun loadFilesList() {
        val call = networkService.filesList
        call?.enqueue(object : Callback<GameFileInfoDto> {
            override fun onResponse(call: Call<GameFileInfoDto>, response: Response<GameFileInfoDto>) {
                if (response.isSuccessful) {
                    response.body()?.let { CacheChecker.setFilesList(this@SplashActivity, it) }
                }
                filesListLoaded = true
                startIfReady()
            }

            override fun onFailure(call: Call<GameFileInfoDto>, t: Throwable) {
                filesListLoaded = true
                startIfReady()
            }
        })
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in permissionList) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_ID)
        } else {
            permissionsGranded = true
            startIfReady()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ID) {
            permissionsGranded = true
            startIfReady()
        }
    }

    fun startIfReady() {
        if (hasStarted) return
        if (permissionsGranded && animationEnded) {
            hasStarted = true
            // Jika file game sudah ada di storage, langsung buka Samp
            val extDir = getExternalFilesDir(null)
            val hasData = File(extDir, "data/gta.dat").exists() || File(extDir, "texdb").exists()
            if (hasData) {
                val gameIntent = Intent(this, Samp::class.java).apply {
                    putExtras(intent)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(gameIntent)
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}
