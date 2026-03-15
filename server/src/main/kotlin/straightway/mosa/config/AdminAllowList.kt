package straightway.mosa.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class AdminAllowList(
    val adminGids: List<String>,
) {
    companion object {
        private val yamlMapper = YAMLMapper().registerKotlinModule()

        val transformer =
            AutoRefreshingFileBackedContent.Transformer { content ->
                val text = content.decodeToString().trim()
                if (text.isEmpty()) {
                    AdminAllowList(emptyList())
                } else {
                    val raw = yamlMapper.readValue(content, AdminAllowList::class.java)
                    raw.copy(adminGids = raw.adminGids.map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
    }
}

operator fun AdminAllowList.contains(gid: String): Boolean = adminGids.contains(gid)
