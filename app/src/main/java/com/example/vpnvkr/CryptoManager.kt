package com.example.vpnvkr

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

class CryptoManager {
    private val key = "32-char-key-for-AES-256-GCM-exam".toByteArray(Charsets.UTF_8) // 32 байта для AES-256
    private val secretKey = SecretKeySpec(key, "AES")
    private val TAG_LENGTH = 128 // Длина тега аутентификации для GCM (в битах)

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) // 12 байт для GCM IV (96 бит)
        SecureRandom().nextBytes(iv) // Генерируем случайный IV
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encrypted = cipher.doFinal(data)
        // Возвращаем IV + зашифрованные данные
        return iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (data.size < 12) { // Проверяем, что есть как минимум IV (12 байт)
            throw IllegalArgumentException("Data too short to contain IV")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = data.copyOfRange(0, 12) // Извлекаем IV из первых 12 байт
        val encryptedData = data.copyOfRange(12, data.size) // Остальное - зашифрованные данные
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedData)
    }
}