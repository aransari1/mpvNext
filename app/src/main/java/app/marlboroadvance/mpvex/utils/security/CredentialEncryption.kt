package app.marlboroadvance.mpvex.utils.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts credentials using the Android Keystore.
 *
 * Passwords are encrypted with AES-256-GCM before being persisted to the Room
 * database, so a physical-access or root attacker cannot read them from the
 * SQLite file.  The key material never leaves the hardware-backed keystore.
 *
 * Encrypted values are stored as Base64 strings prefixed with [ENCRYPTED_PREFIX]
 * so legacy plaintext values can be detected and migrated transparently.
 */
object CredentialEncryption {

  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val KEY_ALIAS = "mpvex_credential_key"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH = 128 // bits
  private const val GCM_IV_LENGTH = 12 // bytes

  /** Prefix that marks a value as already encrypted. */
  const val ENCRYPTED_PREFIX = "ENC:"

  // ── public API ────────────────────────────────────────────────────

  /**
   * Encrypt a plaintext password.
   * Returns a Base64-encoded string prefixed with [ENCRYPTED_PREFIX].
   * Empty / blank passwords are returned as-is.
   */
  fun encrypt(plaintext: String): String {
    if (plaintext.isBlank() || plaintext.startsWith(ENCRYPTED_PREFIX)) return plaintext

    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

    val iv = cipher.iv // GCM generates a random IV
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

    // Store IV + ciphertext together
    val combined = ByteArray(iv.size + ciphertext.size)
    System.arraycopy(iv, 0, combined, 0, iv.size)
    System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

    return ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  /**
   * Decrypt a value previously produced by [encrypt].
   * If the value is not prefixed with [ENCRYPTED_PREFIX] it is assumed to be
   * a legacy plaintext password and returned unchanged.
   */
  fun decrypt(encrypted: String): String {
    if (encrypted.isBlank() || !encrypted.startsWith(ENCRYPTED_PREFIX)) return encrypted

    val combined = Base64.decode(encrypted.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
    val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
    val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

    val cipher = Cipher.getInstance(TRANSFORMATION)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

    return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
  }

  /**
   * Returns `true` when [value] looks like it was already encrypted by this
   * utility.
   */
  fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

  // ── internals ─────────────────────────────────────────────────────

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
      return (entry as KeyStore.SecretKeyEntry).secretKey
    }

    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      KEYSTORE_PROVIDER,
    )
    keyGenerator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return keyGenerator.generateKey()
  }
}
