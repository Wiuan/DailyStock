package com.personal.portfolio

import android.app.Application
import com.personal.portfolio.di.AppContainer

class PortfolioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
