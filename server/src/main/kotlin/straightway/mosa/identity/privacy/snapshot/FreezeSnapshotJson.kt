package straightway.mosa.identity.privacy.snapshot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import straightway.mosa.jackson.InstantJacksonSerializers

class FreezeSnapshotJson {
    private val mapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(InstantJacksonSerializers.createModule())

    fun writeHeader(header: FreezeSnapshotHeader): String = mapper.writeValueAsString(header)

    fun readHeader(json: String): FreezeSnapshotHeader = mapper.readValue(json)

    fun writeManifest(manifest: FreezeSnapshotManifest): String = mapper.writeValueAsString(manifest)

    fun readManifest(json: String): FreezeSnapshotManifest = mapper.readValue(json)

    fun writeRecord(record: FreezeSnapshotRecord): String = mapper.writeValueAsString(record)

    fun readRecord(json: String): FreezeSnapshotRecord = mapper.readValue(json)
}
