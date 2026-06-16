package com.tpms.app.startup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadUnitSupport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isTeyesHeadUnit(): Boolean = TeyesDeviceDetector.isLikelyTeyesHeadUnit(context)
}
