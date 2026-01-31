package straightway.mosa.network

import org.koin.core.module.Module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.TlsConfig

fun Module.configureNetworkAppModule() {
    single {
        val allowHttp =
            get<CommandLineArgumentStore>()
                .getBoolean(NetworkCommandLineOptions.ALLOW_HTTP) ?: false
        TlsConfig(requireTlsForPod = !allowHttp)
    }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val raw = cliArguments.getString(NetworkCommandLineOptions.TRUSTED_PROXY_IPS).orEmpty()
        val trustedProxyIps =
            raw
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        ClientIpResolver(trustedProxyIps)
    }
}
