package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ble.BlePermissionHelper
import com.example.ble.MicrolifeBleManager
import com.example.data.database.BpDatabase
import com.example.data.repository.BpRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MicrolifeTheme
import com.example.ui.viewmodel.BpViewModel
import com.example.ui.viewmodel.BpViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        // Automatische Ausblendung der Telefon-Fußleiste (System-Navigationsleiste / Immersive Sticky Mode)
        // Bei Wischen vom unteren Bildschirmrand wird die Fußleiste kurz eingeblendet und verschwindet danach automatisch wieder
        hideSystemBars()

        if (!BlePermissionHelper.hasPermissions(this)) {
            ActivityCompat.requestPermissions(
                this,
                BlePermissionHelper.getRequiredPermissions(),
                101
            )
        }

        val database = BpDatabase.getDatabase(applicationContext)
        val repository = BpRepository(database.bpDao(), applicationContext)
        val bleManager = MicrolifeBleManager(applicationContext)

        val viewModelFactory = BpViewModelFactory(repository, bleManager, applicationContext)
        val viewModel = ViewModelProvider(this, viewModelFactory)[BpViewModel::class.java]

        setContent {
            MicrolifeTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: Fußleiste blendet sich bei Wischen vom Rand kurz ein und automatisch wieder aus
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Blendet die Navigationsleiste (Fußleiste) aus
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    }

    fun checkBlePermissions(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                    101
                )
            }
        } else {
            // Für ältere Android-Geräte wird Location benötigt, um BLE-Geräte zu finden
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 102)
            }
        }
    }
}
