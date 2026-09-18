package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityManager {
    private const val PREFS_FILE = "secure_credentials_prefs"

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard private preferences if hardware keystore error occurs
            context.getSharedPreferences(PREFS_FILE + "_standard", Context.MODE_PRIVATE)
        }
    }

    fun saveEncrypted(context: Context, key: String, value: String) {
        getSecurePrefs(context).edit().putString(key, value).apply()
    }

    fun getEncrypted(context: Context, key: String, defaultValue: String = ""): String {
        return getSecurePrefs(context).getString(key, defaultValue) ?: defaultValue
    }

    fun clearAll(context: Context) {
        getSecurePrefs(context).edit().clear().apply()
    }
}
