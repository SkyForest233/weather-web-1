package li.drizz.app

import android.app.Application
import li.drizz.app.di.AppGraph

class DrizzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
