package com.tavoohoh.quiet.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon (the Gold icon is a reward, like iOS's alternate icon): two activity aliases
 * of MainActivity, `.LauncherStandard` and `.LauncherGold`, one enabled at a time. Some launchers
 * take a moment to update.
 */
object LauncherIcon {
    private val aliases = listOf("LauncherStandard", "LauncherGold")

    fun set(context: Context, gold: Boolean) {
        val pm = context.packageManager
        for ((i, alias) in aliases.withIndex()) {
            val on = (i == 1) == gold
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { pm.setComponentEnabledSetting(ComponentName(context, "${context.packageName}.$alias"), state, PackageManager.DONT_KILL_APP) }
        }
    }
}
