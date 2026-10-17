package io.github.lihuihnu.xuaneynote

import android.app.Application
import io.github.lihuihnu.xuaneynote.data.LocalData
import io.github.lihuihnu.xuaneynote.model.AutosaveQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class XuaneyApplication : Application() {
    val data by lazy { LocalData(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val saves by lazy { AutosaveQueue(applicationScope, data.notes::save) }
}
