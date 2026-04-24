package app.homefit.infrastructure.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM. 키는 base64-encoded 32 bytes (env: AES_ENCRYPTION_KEY).
 * 저장 포맷: [12B IV][N B ciphertext+tag].
 *
 * 민감 필드(소득/자산 등)에만 사용. 인덱싱/부분 검색 불가 — 그게 필요하면 별도 분리.
 */
@Component
class AesGcmEncryptor(
    @Value("\${homefit.crypto.aes-key-base64}") aesKeyBase64: String,
) {
    private val key: SecretKeySpec = run {
        val decoded = Base64.getDecoder().decode(aesKeyBase64.trim())
        require(decoded.size == 32) { "AES key must decode to 32 bytes (got ${decoded.size})" }
        SecretKeySpec(decoded, "AES")
    }
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray?): ByteArray? {
        if (plaintext == null) return null
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    fun decrypt(ciphertext: ByteArray?): ByteArray? {
        if (ciphertext == null) return null
        require(ciphertext.size > IV_LEN) { "ciphertext too short" }
        val iv = ciphertext.copyOfRange(0, IV_LEN)
        val body = ciphertext.copyOfRange(IV_LEN, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun encryptLong(value: Long?): ByteArray? = value?.let { encrypt(it.toString().toByteArray()) }
    fun decryptLong(ciphertext: ByteArray?): Long? =
        decrypt(ciphertext)?.let { String(it).toLongOrNull() }

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}
