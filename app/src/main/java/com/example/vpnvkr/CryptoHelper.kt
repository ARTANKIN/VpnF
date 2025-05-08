package com.example.vpnvkr

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoHelper(private val key: ByteArray) {

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        // Генерация IV (должна совпадать с серверной реализацией)
        System.arraycopy(key, 0, iv, 0, 16)

        val ivSpec = IvParameterSpec(iv)
        val keySpec = SecretKeySpec(key, "AES")

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data)

        // Объединяем IV и зашифрованные данные
        return iv + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 16)
        val encrypted = data.copyOfRange(16, data.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        val keySpec = SecretKeySpec(key, "AES")

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encrypted)
    }
}