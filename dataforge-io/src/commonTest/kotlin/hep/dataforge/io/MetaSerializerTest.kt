package hep.dataforge.io

import hep.dataforge.io.serialization.NameSerializer
import hep.dataforge.meta.buildMeta
import hep.dataforge.names.toName
import kotlinx.io.charsets.Charsets
import kotlinx.io.core.String
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaSerializerTest {
    @Test
    fun testMetaSerialization() {
        val meta = buildMeta {
            "a" to 22
            "node" to {
                "b" to "DDD"
                "c" to 11.1
                "array" to doubleArrayOf(1.0, 2.0, 3.0)
            }
        }

        val string = Json.indented.stringify(MetaSerializer, meta)
        val restored = Json.plain.parse(MetaSerializer, string)
        assertEquals(restored, meta)
    }

    @Test
    fun testCborSerialization() {
        val meta = buildMeta {
            "a" to 22
            "node" to {
                "b" to "DDD"
                "c" to 11.1
                "array" to doubleArrayOf(1.0, 2.0, 3.0)
            }
        }

        val bytes = Cbor.dump(MetaSerializer, meta)
        println(String(bytes, charset = Charsets.ISO_8859_1))
        val restored = Cbor.load(MetaSerializer, bytes)
        assertEquals(restored, meta)
    }

    @Test
    fun testNameSerialization() {
        val name = "a.b.c".toName()
        val string = Json.indented.stringify(NameSerializer, name)
        val restored = Json.plain.parse(NameSerializer, string)
        assertEquals(restored, name)
    }
}