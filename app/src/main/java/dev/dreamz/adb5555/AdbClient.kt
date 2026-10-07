package dev.dreamz.adb5555

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * An ADB client for this phone's own adbd. Pairs once with Wireless debugging (the phone then
 * trusts this app's key, like a computer's), and afterwards asks adbd to switch to TCP port 5555,
 * which is exactly what `adb tcpip 5555` does. The shell (and so Shizuku) can't do this itself on
 * Android 11+: setting service.adb.tcp.port or restarting adbd is denied to it.
 */
class AdbClient private constructor(private val context: Context) : AbsAdbConnectionManager() {

    private val keyFile = File(context.filesDir, "adb_key.pk8")
    private val certFile = File(context.filesDir, "adb_cert.der")
    private val key: PrivateKey
    private val cert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        setTimeout(10, TimeUnit.SECONDS)
        if (keyFile.isFile && certFile.isFile) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val name = X500Name("CN=ADB 5555")
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 30L * 365 * 86_400_000L), name, pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            key = pair.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    /** Shown under Wireless debugging › Paired devices. */
    override fun getDeviceName(): String = "ADB 5555 app"

    sealed interface Outcome {
        data object Done : Outcome
        data object AlreadyOn : Outcome
        data object NeedsPairing : Outcome
        /** No Wireless debugging service found: it's off, or the phone isn't on Wi-Fi. */
        data object WirelessDebuggingOff : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Pairs with the code shown in "Pair device with pairing code"; [port] is that dialog's port. */
    suspend fun pairWithCode(port: Int, code: String): Outcome = io {
        try {
            if (pair(LOOPBACK, port, code)) {
                prefs(context).edit().putBoolean(KEY_PAIRED, true).apply()
                Outcome.Done
            } else {
                Outcome.Failed("The phone rejected the code. Check it and try again.")
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Pairing failed (${e.javaClass.simpleName}).")
        }
    }

    /** `adb tcpip 5555`, sent over Wireless debugging. */
    suspend fun enable(): Outcome = io {
        if (isPortOpen()) return@io Outcome.AlreadyOn
        try {
            if (!connectTls(context, 8_000)) return@io Outcome.WirelessDebuggingOff
            send("tcpip:$PORT")
        } catch (e: AdbPairingRequiredException) {
            return@io Outcome.NeedsPairing
        } catch (e: Exception) {
            return@io Outcome.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { disconnect() }
        }
        // adbd restarts itself; give it a few seconds to come back listening on 5555.
        repeat(20) { if (isPortOpen()) return@io Outcome.Done; delay(250) }
        Outcome.Failed("adbd didn't come back on port $PORT.")
    }

    /** `adb usb`: back to USB only, sent over port 5555 itself (the paired key is trusted there too). */
    suspend fun disable(): Outcome = io {
        if (!isPortOpen()) return@io Outcome.Done
        try {
            if (!connect(LOOPBACK, PORT)) return@io Outcome.Failed("Couldn't connect to port $PORT.")
            send("usb:")
        } catch (e: AdbPairingRequiredException) {
            return@io Outcome.NeedsPairing
        } catch (e: Exception) {
            return@io Outcome.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { disconnect() }
        }
        repeat(20) { if (!isPortOpen()) return@io Outcome.Done; delay(250) }
        Outcome.Failed("Port $PORT is still open.")
    }

    /** Opens [service] and reads its reply until adbd closes the stream (it restarts right after). */
    private fun send(service: String): String {
        val stream = openStream(service)
        val reply = StringBuilder()
        runCatching {
            stream.openInputStream().use { input ->
                val buf = ByteArray(256)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    reply.append(String(buf, 0, n))
                }
            }
        }
        runCatching { stream.close() }
        return reply.toString()
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    companion object {
        const val PORT = 5555
        private const val LOOPBACK = "127.0.0.1"
        private const val KEY_PAIRED = "paired"
        private val lock = Mutex()

        @Volatile private var instance: AdbClient? = null
        fun get(context: Context): AdbClient =
            instance ?: synchronized(this) { instance ?: AdbClient(context.applicationContext).also { instance = it } }

        fun prefs(context: Context) = context.getSharedPreferences("adb5555", Context.MODE_PRIVATE)
        fun isPaired(context: Context) = prefs(context).getBoolean(KEY_PAIRED, false)

        /** True while adbd accepts plain TCP on 5555 (i.e. after `adb tcpip 5555`). */
        fun isPortOpen(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress(LOOPBACK, PORT), 300); true }
        }.getOrDefault(false)
    }
}
