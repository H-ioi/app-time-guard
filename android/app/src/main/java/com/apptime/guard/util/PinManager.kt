package com.apptime.guard.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.apptime.guard.data.prefs.SettingsRepository
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * PIN 安全存储：Keystore 加密盐值 + SHA-256 迭代哈希。
 * 密钥不可导出（Keystore），PIN 永不落盘明文。
 */
class PinManager(private val settings: SettingsRepository) {

    private val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val existing = keystore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 设置 PIN：生成随机盐，Keystore 加密后存储 */
    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encrypted = encrypt(salt)
        val hash = hashPin(pin, encrypted)
        settings.setPinHash(Base64.encodeToString(hash, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    suspend fun verify(pin: String): Boolean {
        val saltB64 = settings.getPinSalt() ?: return false
        val encrypted = Base64.decode(saltB64, Base64.NO_WRAP)
        val hash = hashPin(pin, encrypted)
        val stored = settings.getPinHash() ?: return false
        return MessageDigest.isEqual(hash, Base64.decode(stored, Base64.NO_WRAP))
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        var out = salt + pin.toByteArray()
        repeat(1000) { out = digest.digest(out) }
        return out
    }

    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv + cipher.doFinal(data)
    }

    private companion object {
        const val KEY_ALIAS = "apptime_pin_key"
    }
}
