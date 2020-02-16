package hep.dataforge.tables

import kotlin.reflect.KClass

/**
 * @param C bottom type for all columns in the table
 */
class ColumnTable<C : Any>(override val columns: Collection<Column<C>>) : Table<C> {
    private val rowsNum = columns.first().size

    init {
        require(columns.all { it.size == rowsNum }) { "All columns must be of the same size" }
    }

    override val rows: List<Row<C>>
        get() = (0 until rowsNum).map { VirtualRow(this, it) }

    override fun <T : C> getValue(row: Int, column: String, type: KClass<out T>): T? {
        val value = columns[column]?.get(row)
        return type.cast(value)
    }
}

internal class VirtualRow<C : Any>(val table: Table<C>, val index: Int) : Row<C> {
    override fun <T : C> getValue(column: String, type: KClass<out T>): T? = table.getValue(index, column, type)

//    override fun <T : C> get(columnHeader: ColumnHeader<T>): T? {
//        return table.co[columnHeader][index]
//    }
}

