package pl.izzy.zlap.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Trwały link do rozmowy z konkretną osobą (WhatsApp, SMS) na podstawie numeru –
 * z powiadomienia albo znaleziony w kontaktach po nazwie czatu.
 */
object ContactLinks {
    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
    )

    fun hasPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Link dla rozmowy o nazwie [name] w aplikacji [pkg] (np. „Mama” w WhatsAppie). */
    fun linkForName(c: Context, pkg: String?, name: String?): String? {
        if (pkg == null || name.isNullOrBlank() || !supports(c, pkg)) return null
        // Sam numer (np. SMS od nieznanego) – nie trzeba szukać w kontaktach
        if (name.all { it.isDigit() || it in "+ -()" }) return linkForNumber(c, pkg, name)
        return phoneFor(c, name.trim())?.let { linkForNumber(c, pkg, it) }
    }

    fun linkForNumber(c: Context, pkg: String, number: String): String? {
        val e164 = toE164(c, number) ?: return null
        return when {
            pkg.startsWith("com.whatsapp") -> "https://wa.me/${e164.trimStart('+')}"
            isSmsApp(c, pkg) -> "smsto:$e164"
            else -> null
        }
    }

    private fun supports(c: Context, pkg: String) = pkg.startsWith("com.whatsapp") || isSmsApp(c, pkg)

    private fun isSmsApp(c: Context, pkg: String) =
        pkg in SMS_APPS || pkg == Telephony.Sms.getDefaultSmsPackage(c)

    private fun phoneFor(c: Context, name: String): String? {
        if (!hasPermission(c)) return null
        return try {
            c.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.NORMALIZED_NUMBER, Phone.NUMBER),
                "${Phone.DISPLAY_NAME} = ? COLLATE NOCASE",
                arrayOf(name),
                "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: cursor.getString(1) else null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Numer w formacie międzynarodowym (+48...), z krajem karty SIM dla numerów lokalnych. */
    private fun toE164(c: Context, number: String): String? {
        val trimmed = number.filter { it.isDigit() || it == '+' }
        if (trimmed.startsWith("+")) return trimmed
        if (trimmed.startsWith("00")) return "+" + trimmed.drop(2)
        val country = c.getSystemService(TelephonyManager::class.java)?.simCountryIso
            ?.uppercase(Locale.ROOT)
            ?.ifEmpty { null }
            ?: "PL"
        return PhoneNumberUtils.formatNumberToE164(trimmed, country)
    }
}
