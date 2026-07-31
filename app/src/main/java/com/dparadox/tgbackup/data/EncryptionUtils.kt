package com.dparadox.tgbackup.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun encrypt(data: ByteArray, keyString: String): ByteArray {
        val key = SecretKeySpec(keyString.substring(0, 32).toByteArray(), "AES")
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext // Prepend IV
    }

    fun decrypt(encryptedData: ByteArray, keyString: String): ByteArray {
        val key = SecretKeySpec(keyString.substring(0, 32).toByteArray(), "AES")
        val iv = encryptedData.sliceArray(0 until IV_LENGTH_BYTE)
        val ciphertext = encryptedData.sliceArray(IV_LENGTH_BYTE until encryptedData.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        
        return cipher.doFinal(ciphertext)
    }
}
