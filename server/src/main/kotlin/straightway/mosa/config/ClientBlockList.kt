package straightway.mosa.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.commons.validator.routines.InetAddressValidator
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class ClientBlockList(
    val blockedClients: List<BlockedClient>,
) {
    data class BlockedClient(
        val address: InetAddress,
        val prefixLength: Int,
    ) {
        override fun toString(): String =
            if (prefixLength == address.maxPrefixLength()) {
                address.hostAddress
            } else {
                "${address.hostAddress}/$prefixLength"
            }
    }

    companion object {
        private val yamlMapper = YAMLMapper().registerKotlinModule()
        private val inetAddressValidator = InetAddressValidator.getInstance()

        val transformer =
            AutoRefreshingFileBackedContent.Transformer { content ->
                val text = content.decodeToString().trim()
                if (text.isEmpty()) {
                    ClientBlockList(emptyList())
                } else {
                    val raw = yamlMapper.readValue(content, RawClientBlockList::class.java)
                    ClientBlockList(raw.blockedClients.map { parseBlockedClient(it) })
                }
            }

        private data class RawClientBlockList(
            val blockedClients: List<String> = emptyList(),
        )

        private fun parseBlockedClient(entry: String): BlockedClient {
            val trimmed = entry.trim()
            require(trimmed.isNotEmpty()) { "Blocked client entry must not be blank" }

            val parts = trimmed.split("/", limit = 2)
            val ipText = parts[0].trim()
            val prefixText = parts.getOrNull(1)?.trim()

            require(ipText.isNotEmpty()) { "Blocked client entry must contain an IP address: $entry" }
            require(isValidIp(ipText)) { "Invalid IP address: $entry" }

            val address = InetAddress.getByName(ipText)
            val maxPrefixLength = address.maxPrefixLength()
            val prefixLength =
                when {
                    prefixText.isNullOrBlank() -> maxPrefixLength
                    else -> prefixText.toIntOrNull() ?: throw IllegalArgumentException("Invalid prefix length: $entry")
                }

            require(prefixLength in 0..maxPrefixLength) { "Invalid prefix length: $entry" }

            return BlockedClient(address, prefixLength)
        }

        private fun isValidIp(value: String): Boolean =
            inetAddressValidator.isValidInet4Address(value) || inetAddressValidator.isValidInet6Address(value)

        private fun InetAddress.maxPrefixLength(): Int =
            when (this) {
                is Inet4Address -> 32
                is Inet6Address -> 128
                else -> throw IllegalArgumentException("Unsupported IP address type: ${this::class.qualifiedName}")
            }
    }
}

operator fun ClientBlockList.contains(address: InetAddress): Boolean = blockedClients.any { it.matches(address) }

fun ClientBlockList.BlockedClient.matches(address: InetAddress): Boolean {
    if (this.address.javaClass != address.javaClass) {
        return false
    }

    val addressBytes = address.address
    val blockedBytes = this.address.address
    val fullBytes = prefixLength / 8
    val remainingBits = prefixLength % 8

    if (!addressBytes.copyOfRange(0, fullBytes).contentEquals(blockedBytes.copyOfRange(0, fullBytes))) {
        return false
    }

    if (remainingBits == 0) {
        return true
    }

    val mask = (0xFF shl (8 - remainingBits)) and 0xFF
    return (addressBytes[fullBytes].toInt() and mask) == (blockedBytes[fullBytes].toInt() and mask)
}
