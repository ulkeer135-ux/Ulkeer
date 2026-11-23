package com.annem.launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Cihaz yöneticisi alıcısı: yalnızca "ekranı kilitle" yetkisi ister (force-lock). */
class AdminReceiver : DeviceAdminReceiver()

enum class LockResult { LOCKED, NOT_SET_UP, SERVICE_NOT_CONNECTED }

/**
 * "Ekranı Kapat" iki yoldan çalışır:
 * 1. Erişilebilirlik servisi (önerilen): güç tuşu gibi; parmak izi kilidi aynen çalışır.
 * 2. Cihaz yöneticisi (yedek): tek onayla açılır, ama kilitten sonraki ilk açılışta
 *    Android parmak izi yerine PIN/desen isteyebilir.
 */
object ScreenLock {

    fun lock(context: Context): LockResult {
        if (LockService.lock()) return LockResult.LOCKED
        if (isAdminActive(context)) {
            try {
                policyManager(context).lockNow()
                return LockResult.LOCKED
            } catch (e: SecurityException) {
                // Yetki geri alınmış: aşağıdaki kurulum mesajına düş.
            }
        }
        return if (LockService.isEnabled(context)) LockResult.SERVICE_NOT_CONNECTED else LockResult.NOT_SET_UP
    }

    fun isAdminActive(context: Context): Boolean = policyManager(context).isAdminActive(adminComponent(context))

    fun adminActivationIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Ana ekrandaki \"Ekranı Kapat\" düğmesinin telefonu kilitleyebilmesi için gerekli. " +
                    "Başka hiçbir yetki kullanılmaz.",
            )

    /** Uygulamayı kaldırmadan önce cihaz yöneticisinin kapatılması gerekir. */
    fun removeAdmin(context: Context) {
        policyManager(context).removeActiveAdmin(adminComponent(context))
    }

    private fun adminComponent(context: Context) = ComponentName(context, AdminReceiver::class.java)

    private fun policyManager(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
}
