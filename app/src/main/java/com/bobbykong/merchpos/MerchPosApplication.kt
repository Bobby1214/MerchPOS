package com.bobbykong.merchpos

import android.app.Application
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.PosRepository

/** Lives as long as the app. Holds the one repository that all ViewModels share. */
class MerchPosApplication : Application() {
    val repository: PosRepository by lazy { PosRepository(AppDatabase.get(this)) }
}
