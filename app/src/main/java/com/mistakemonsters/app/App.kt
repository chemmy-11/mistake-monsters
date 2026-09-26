package com.mistakemonsters.app

import android.app.Application
import com.mistakemonsters.app.data.Db
import com.mistakemonsters.app.data.Prefs
import com.mistakemonsters.app.logic.Pipeline
import com.mistakemonsters.app.update.UpdateManager

// ===== 应用单例容器 =====

class App : Application() {
    lateinit var db: Db
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var pipeline: Pipeline
        private set
    lateinit var updater: UpdateManager
        private set

    /** 练习页要针对的错因（从画像页跳转时设置） */
    var lastCauseId: Long? = null

    override fun onCreate() {
        super.onCreate()
        db = Db.get(this)
        prefs = Prefs.get(this)
        pipeline = Pipeline(db, prefs)
        updater = UpdateManager(this)
    }
}
