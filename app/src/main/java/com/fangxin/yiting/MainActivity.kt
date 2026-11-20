package com.fangxin.yiting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.fangxin.yiting.ui.YitingApp

class MainActivity : ComponentActivity() {
    private lateinit var machine: VoiceMachine

    private val micLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                machine.beginListening()
            } else {
                machine.micDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        machine = VoiceMachine(application)
        setContent {
            YitingApp(
                machine = machine,
                onTalk = {
                    if (machine.phase == Phase.LISTENING) {
                        machine.onPrimaryTap()
                    } else if (hasMic()) {
                        machine.onPrimaryTap()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        machine.shutdown()
        super.onDestroy()
    }
}
