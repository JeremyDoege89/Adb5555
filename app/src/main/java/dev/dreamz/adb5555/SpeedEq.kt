package dev.dreamz.adb5555

import android.content.Context
import android.content.pm.PackageManager

/** The SpeedEQ app, which needs a one-time shell grant this app can do (see [AdbClient.grantSpeedEq]). */
object SpeedEq {
    const val PACKAGE = "dev.dreamz.speedeq"
    const val PERMISSION = "android.permission.DUMP"

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun hasAccess(context: Context): Boolean =
        context.packageManager.checkPermission(PERMISSION, PACKAGE) == PackageManager.PERMISSION_GRANTED
}
