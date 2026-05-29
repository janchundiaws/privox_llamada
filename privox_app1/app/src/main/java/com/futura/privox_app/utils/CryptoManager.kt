package com.futura.privox_app.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clase para cifrar y descifrar datos utilizando AES.
 * Por seguridad en producción, la 'key' debería gestionarse a través de Android KeyStore.
 */
object CryptoManager {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    
    // Una clave de 16, 24 o 32 bytes para AES-128, 192 o 256 respectivamente.
    // IMPORTANTE: Cambia esta clave por una secreta y no la compartas.
    private const val SECRET_KEY = "LaVidaEsUnaAQUIE" // 16 bytes para AES-128
    private const val IV = "PrivoxVectorInit"       // 16 bytes para el vector de inicialización

    private val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
    private val ivSpec = IvParameterSpec(IV.toByteArray())

    /**
     * Cifra una cadena de texto.
     * @return El texto cifrado codificado en Base64.
     */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Descifra una cadena de texto en Base64.
     * @return El texto original (plano).
     */
    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
