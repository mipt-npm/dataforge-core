package space.kscience.dataforge.meta

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.NameToken
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.plus
import kotlin.collections.set
import kotlin.jvm.Synchronized

//TODO add validator to configuration

/**
 * Mutable meta representing object state
 */
@Serializable(Config.Companion::class)
public class Config : AbstractMutableMeta<Config>(), ItemPropertyProvider {

    private val listeners = HashSet<ItemListener>()

    @Synchronized
    private fun itemChanged(name: Name, oldItem: MetaItem?, newItem: MetaItem?) {
        listeners.forEach { it.action(name, oldItem, newItem) }
    }

    /**
     * Add change listener to this meta. Owner is declared to be able to remove listeners later. Listener without owner could not be removed
     */
    @Synchronized
    override fun onChange(owner: Any?, action: (Name, MetaItem?, MetaItem?) -> Unit) {
        listeners.add(ItemListener(owner, action))
    }

    /**
     * Remove all listeners belonging to given owner
     */
    @Synchronized
    override fun removeListener(owner: Any?) {
        listeners.removeAll { it.owner === owner }
    }

    override fun replaceItem(key: NameToken, oldItem: TypedMetaItem<Config>?, newItem: TypedMetaItem<Config>?) {
        if (newItem == null) {
            children.remove(key)
            if (oldItem != null && oldItem is MetaItemNode<Config>) {
                oldItem.node.removeListener(this)
            }
        } else {
            children[key] = newItem
            if (newItem is MetaItemNode) {
                newItem.node.onChange(this) { name, oldChild, newChild ->
                    itemChanged(key + name, oldChild, newChild)
                }
            }
        }
        itemChanged(key.asName(), oldItem, newItem)
    }

    /**
     * Attach configuration node instead of creating one
     */
    override fun wrapNode(meta: Meta): Config = meta.asConfig()

    override fun empty(): Config = Config()

    public companion object ConfigSerializer : KSerializer<Config> {

        public fun empty(): Config = Config()
        override val descriptor: SerialDescriptor get() = MetaSerializer.descriptor

        override fun deserialize(decoder: Decoder): Config {
            return MetaSerializer.deserialize(decoder).asConfig()
        }

        override fun serialize(encoder: Encoder, value: Config) {
            MetaSerializer.serialize(encoder, value)
        }
    }
}

public operator fun Config.get(token: NameToken): TypedMetaItem<Config>? = items[token]

/**
 * Create a mutable copy of this [Meta]. The copy is created event if initial [Meta] is a [Config].
 * Listeners are not preserved
 */
public fun Meta.toConfig(): Config = Config().also { builder ->
    this.items.mapValues { entry ->
        val item = entry.value
        builder[entry.key.asName()] = when (item) {
            is MetaItemValue -> item.value
            is MetaItemNode -> MetaItemNode(item.node.asConfig())
        }
    }
}

/**
 * Create a copy of this config, optionally applying the given [block].
 * The listeners of the original Config are not retained.
 */
public inline fun Config.copy(block: Config.() -> Unit = {}): Config = toConfig().apply(block)

/**
 * Return this [Meta] as [Config] if it is [Config] and create a new copy otherwise
 */
public fun Meta.asConfig(): Config = this as? Config ?: toConfig()