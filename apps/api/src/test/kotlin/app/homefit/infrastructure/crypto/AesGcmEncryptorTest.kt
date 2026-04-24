package app.homefit.infrastructure.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class AesGcmEncryptorTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val enc = AesGcmEncryptor(key)

    @Test
    fun `encrypt then decrypt round trip`() {
        val plain = "hello world".toByteArray()
        val ct = enc.encrypt(plain)!!
        assertThat(ct).isNotEqualTo(plain)
        assertThat(enc.decrypt(ct)).isEqualTo(plain)
    }

    @Test
    fun `each encryption produces different ciphertext (random IV)`() {
        val plain = "same input".toByteArray()
        val a = enc.encrypt(plain)!!
        val b = enc.encrypt(plain)!!
        assertThat(a).isNotEqualTo(b)
        assertThat(enc.decrypt(a)).isEqualTo(plain)
        assertThat(enc.decrypt(b)).isEqualTo(plain)
    }

    @Test
    fun `encryptLong decryptLong round trip`() {
        val v = 1_234_567_890L
        val ct = enc.encryptLong(v)!!
        assertThat(enc.decryptLong(ct)).isEqualTo(v)
    }

    @Test
    fun `null in returns null out`() {
        assertThat(enc.encrypt(null)).isNull()
        assertThat(enc.decrypt(null)).isNull()
        assertThat(enc.encryptLong(null)).isNull()
        assertThat(enc.decryptLong(null)).isNull()
    }
}
