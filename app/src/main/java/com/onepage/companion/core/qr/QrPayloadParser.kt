package com.onepage.companion.core.qr

/**
 * Decodes what the device actually prints on its screen
 * (see `CrossPointWebServerActivity::renderServerRunning`):
 *
 *   AP mode:    WIFI:S:CrossPoint-Reader;;        ← join the device hotspot
 *               http://192.168.4.1/               ← reach the web UI
 *   STA mode:   http://192.168.1.42/
 *
 * The firmware's Wi-Fi payload omits `T:` and `P:` because the AP is open, so
 * the parser treats a missing type as "no passphrase" instead of rejecting it.
 */
object QrPayloadParser {

    sealed interface Payload {
        data class Wifi(val ssid: String, val password: String?, val security: String?) : Payload {
            val isOpen: Boolean get() = password.isNullOrEmpty()
            val securityLabel: String
                get() = when {
                    isOpen -> "开放网络"
                    security.equals("WEP", true) -> "WEP"
                    security.equals("SAE", true) -> "WPA3"
                    else -> "WPA/WPA2"
                }
        }

        data class Endpoint(val host: String, val port: Int = 80) : Payload
    }

    fun parse(raw: String): Payload? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("WIFI:", ignoreCase = true)) {
            val fields = splitFields(text.substring(5))
            val ssid = fields["S"]?.trim().orEmpty()
            if (ssid.isEmpty()) return null
            val password = fields["P"]?.takeIf { it.isNotEmpty() }
            val security = fields["T"]?.takeIf { it.isNotBlank() }
            return Payload.Wifi(ssid = ssid, password = password, security = security)
        }

        val urlish = text.removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (urlish.isEmpty()) return null

        // host[:port][/path]
        val hostPort = urlish.substringBefore('/')
        if (hostPort.isEmpty()) return null

        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "").toIntOrNull()?.takeIf { it in 1..65535 } ?: 80

        val looksLikeHost = host.matches(Regex("""^[0-9A-Za-z]([0-9A-Za-z.-]*[0-9A-Za-z])?$"""))
        if (!looksLikeHost) return null
        // Avoid treating arbitrary dictionary words as a device address.
        val looksLikeIpv4 = host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
        val hasDot = host.contains('.')
        if (!looksLikeIpv4 && !hasDot) return null

        return Payload.Endpoint(host = host, port = port)
    }

    /** Splits `K:value;K:value;;` honouring the `\;` `\,` `\:` `\\` escapes. */
    private fun splitFields(body: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val value = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    value.append(body[i + 1])
                    i += 2
                }
                c == ':' && key == null -> {
                    key = value.toString()
                    value.clear()
                    i++
                }
                c == ';' -> {
                    key?.let { result[it] = value.toString() }
                    key = null
                    value.clear()
                    i++
                }
                else -> {
                    value.append(c)
                    i++
                }
            }
        }
        if (key != null) result[key!!] = value.toString()
        return result
    }
}
