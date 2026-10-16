package io.github.lihuihnu.xuaneynote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lihuihnu.xuaneynote.ui.NoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NoteApp() }
    }
    override fun onStop() {
        (application as XuaneyApplication).saves.requestFlush()
        super.onStop()
    }
}
