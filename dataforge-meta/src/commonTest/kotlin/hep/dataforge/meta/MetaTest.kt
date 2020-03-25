package hep.dataforge.meta

import hep.dataforge.values.NumberValue
import hep.dataforge.values.True
import hep.dataforge.values.Value
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaTest {
    @Test
    fun valueEqualityTest() {
        assertEquals(NumberValue(22), NumberValue(22))
        assertEquals(NumberValue(22.0), NumberValue(22))
        assertEquals(True, Value.of(true))
    }

    @Test
    fun metaEqualityTest() {
        val meta1 = Meta {
            "a" put 22
            "b" put {
                "c" put "ddd"
            }
        }
        val meta2 = Meta {
            "b" put {
                "c" put "ddd"
            }
            "a" put 22
        }.seal()
        assertEquals<Meta>(meta1, meta2)
    }

    @Test
    fun metaToMap(){
        val meta = Meta {
            "a" put 22
            "b" put {
                "c" put "ddd"
            }
            "list" put (0..4).map {
                Meta {
                    "value" put it
                }
            }
        }
        val map = meta.toMap()
        val reconstructed = map.toMeta()

        assertEquals(meta,reconstructed)
    }
}