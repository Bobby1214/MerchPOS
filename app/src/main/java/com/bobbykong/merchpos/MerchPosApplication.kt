package com.bobbykong.merchpos

import android.app.Application
import com.bobbykong.merchpos.data.AppDatabase
import com.bobbykong.merchpos.data.PosRepository
import com.bobbykong.merchpos.data.files.BackupManager

/** Lives as long as the app. Holds the one repository that all ViewModels share. */
class MerchPosApplication : Application() {
    val repository: PosRepository by lazy { PosRepository(AppDatabase.get(this)) }
    val backupManager: BackupManager by lazy { BackupManager(this, AppDatabase.get(this)) }
}
