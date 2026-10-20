package com.bobbykong.merchpos.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bobbykong.merchpos.MerchPosApplication
import com.bobbykong.merchpos.ui.events.EventsViewModel
import com.bobbykong.merchpos.ui.orders.OrdersViewModel
import com.bobbykong.merchpos.ui.products.ProductsViewModel
import com.bobbykong.merchpos.ui.report.ReportViewModel
import com.bobbykong.merchpos.ui.sell.SellViewModel

/** Tells Compose how to build each ViewModel: give it the shared repository. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SellViewModel(repository()) }
        initializer { ProductsViewModel(repository(), app()) }
        initializer { OrdersViewModel(repository()) }
        initializer { ReportViewModel(repository()) }
        initializer { EventsViewModel(repository(), app().backupManager) }
    }
}

private fun CreationExtras.app() = this[APPLICATION_KEY] as MerchPosApplication

private fun CreationExtras.repository() = app().repository
