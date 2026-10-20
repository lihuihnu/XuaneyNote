package io.github.lihuihnu.xuaneynote

import android.app.Application
import io.github.lihuihnu.xuaneynote.data.LocalData
import io.github.lihuihnu.xuaneynote.model.AutosaveQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XuaneyApplication : Application() {
    val data by lazy { LocalData(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val saves by lazy { AutosaveQueue(applicationScope, data.notes::save) }

    override fun onCreate() {
        super.onCreate()
        // 进程启动后在 IO 线程恢复私有目录；永不删除 Room 仍引用的图片。
        applicationScope.launch(Dispatchers.IO) {
            runCatching { data.bundle.cleanupInspections() }
            runCatching { data.encryptedBundles.cleanupOrphans() }
            runCatching { data.attachments.recover() }
        }
    }
}
