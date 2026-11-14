package com.tavoohoh.quietwords.design

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tavoohoh.quietwords.core.AppIconChoice

/**
 * The launcher icon (the Gold icon is a reward, like iOS's alternate icon): two activity
 * aliases of MainActivity, one enabled at a time. Some launchers take a moment to update.
 */
object LauncherIcon {
    private fun alias(context: Context, choice: AppIconChoice) = ComponentName(
        context, "${context.packageName}.${if (choice == AppIconChoice.GOLD) "LauncherGold" else "LauncherStandard"}",
    )

    fun set(context: Context, choice: AppIconChoice) {
        val pm = context.packageManager
        for (option in AppIconChoice.entries) {
            val state = if (option == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { pm.setComponentEnabledSetting(alias(context, option), state, PackageManager.DONT_KILL_APP) }
        }
    }
}
