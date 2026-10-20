package com.bobbykong.merchpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bobbykong.merchpos.ui.MerchPosRoot
import com.bobbykong.merchpos.ui.theme.MerchPOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MerchPOSTheme {
                MerchPosRoot()
            }
        }
    }
}
