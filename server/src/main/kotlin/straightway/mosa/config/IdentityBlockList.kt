package straightway.mosa.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class IdentityBlockList(
    val blockedGids: List<String>,
) {
    companion object {
        private val yamlMapper = YAMLMapper().registerKotlinModule()

        val transformer =
            AutoRefreshingFileBackedContent.Transformer { content ->
                val text = content.decodeToString().trim()
                if (text.isEmpty()) {
                    IdentityBlockList(emptyList())
                } else {
                    val raw = yamlMapper.readValue(content, IdentityBlockList::class.java)
                    raw.copy(blockedGids = raw.blockedGids.map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
    }
}

operator fun IdentityBlockList.contains(gid: String): Boolean = blockedGids.contains(gid)
