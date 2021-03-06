package space.kscience.dataforge.meta.descriptors

import space.kscience.dataforge.meta.*
import space.kscience.dataforge.misc.DFBuilder
import space.kscience.dataforge.names.*


/**
 * Descriptor for meta node. Could contain additional information for viewing
 * and editing.
 *
 * @author Alexander Nozik
 */
@DFBuilder
public sealed interface NodeDescriptor: ItemDescriptor {
    /**
     * True if the node is required
     *
     * @return
     */
    override val required: Boolean

    /**
     * The default for this node. Null if there is no default.
     *
     * @return
     */
    public val default: Config?

    /**
     * The map of children item descriptors (both nodes and values)
     */
    public val items: Map<String, ItemDescriptor>

    /**
     * The map of children node descriptors
     */
    public val nodes: Map<String, NodeDescriptor>

    /**
     * The list of children value descriptors
     */
    public val values: Map<String, ValueDescriptor>

    public companion object {

        internal val ITEM_KEY: Name = "item".asName()
        internal val IS_NODE_KEY: Name = "@isNode".asName()

        //TODO infer descriptor from spec
    }
}


@DFBuilder
public class NodeDescriptorBuilder(config: Config = Config()) : ItemDescriptorBuilder(config), NodeDescriptor {
    init {
        config[IS_NODE_KEY] = true
    }

    /**
     * True if the node is required
     *
     * @return
     */
    override var required: Boolean by config.boolean { default == null }

    /**
     * The default for this node. Null if there is no default.
     *
     * @return
     */
    override var default: Config? by config.node()

    /**
     * The map of children item descriptors (both nodes and values)
     */
    override val items: Map<String, ItemDescriptor>
        get() = config.getIndexed(ITEM_KEY).entries.associate { (name, item) ->
            if (name == null) error("Child item index should not be null")
            val node = item.node ?: error("Node descriptor must be a node")
            if (node[IS_NODE_KEY].boolean == true) {
                name to NodeDescriptorBuilder(node as Config)
            } else {
                name to ValueDescriptorBuilder(node as Config)
            }
        }

    /**
     * The map of children node descriptors
     */
    @Suppress("UNCHECKED_CAST")
    override val nodes: Map<String, NodeDescriptor>
        get() = config.getIndexed(ITEM_KEY).entries.filter {
            it.value.node[IS_NODE_KEY].boolean == true
        }.associate { (name, item) ->
            if (name == null) error("Child node index should not be null")
            val node = item.node ?: error("Node descriptor must be a node")
            name to NodeDescriptorBuilder(node as Config)
        }

    /**
     * The list of children value descriptors
     */
    override val values: Map<String, ValueDescriptor>
        get() = config.getIndexed(ITEM_KEY).entries.filter {
            it.value.node[IS_NODE_KEY].boolean != true
        }.associate { (name, item) ->
            if (name == null) error("Child value index should not be null")
            val node = item.node ?: error("Node descriptor must be a node")
            name to ValueDescriptorBuilder(node as Config)
        }

    private fun buildNode(name: Name): NodeDescriptorBuilder {
        return when (name.length) {
            0 -> this
            1 -> {
                val token = NameToken(ITEM_KEY.toString(), name.toString())
                val config: Config = config[token].node ?: Config().also {
                    it[IS_NODE_KEY] = true
                    config[token] = it
                }
                NodeDescriptorBuilder(config)
            }
            else -> buildNode(name.firstOrNull()?.asName()!!).buildNode(name.cutFirst())
        }
    }

    /**
     * Define a child item descriptor for this node
     */
    private fun newItem(key: String, descriptor: ItemDescriptor) {
        if (items.keys.contains(key)) error("The key $key already exists in descriptor")
        val token = ITEM_KEY.withIndex(key)
        config[token] = descriptor.toMeta()
    }

    public fun item(name: Name, descriptor: ItemDescriptor) {
        buildNode(name.cutLast()).newItem(name.lastOrNull().toString(), descriptor)
    }

    public fun item(name: String, descriptor: ItemDescriptor) {
        item(name.toName(), descriptor)
    }

    /**
     * Create and configure a child node descriptor
     */
    public fun node(name: Name, block: NodeDescriptorBuilder.() -> Unit) {
        item(name, NodeDescriptorBuilder().apply(block))
    }

    public fun node(name: String, block: NodeDescriptorBuilder.() -> Unit) {
        node(name.toName(), block)
    }

    /**
     * Create and configure child value descriptor
     */
    public fun value(name: Name, block: ValueDescriptorBuilder.() -> Unit) {
        require(name.length >= 1) { "Name length for value descriptor must be non-empty" }
        item(name, ValueDescriptorBuilder().apply(block))
    }

    public fun value(name: String, block: ValueDescriptorBuilder.() -> Unit) {
        value(name.toName(), block)
    }

    override fun build(): NodeDescriptor = NodeDescriptorBuilder(config.copy())

    public companion object {

        internal val ITEM_KEY: Name = "item".asName()
        internal val IS_NODE_KEY: Name = "@isNode".asName()

        //TODO infer descriptor from spec
    }
}

public inline fun NodeDescriptor(block: NodeDescriptorBuilder.() -> Unit): NodeDescriptor =
    NodeDescriptorBuilder().apply(block)

/**
 * Merge two node descriptors into one using first one as primary
 */
public operator fun NodeDescriptor.plus(other: NodeDescriptor): NodeDescriptor {
    return NodeDescriptorBuilder().apply {
        config.update(other.toMeta())
        config.update(this@plus.toMeta())
    }
}