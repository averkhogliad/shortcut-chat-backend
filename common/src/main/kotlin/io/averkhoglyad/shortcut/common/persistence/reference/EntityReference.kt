package io.averkhoglyad.shortcut.common.persistence.reference

import io.averkhoglyad.shortcut.common.persistence.id.IdentifiedEntity
import org.springframework.data.jdbc.core.mapping.AggregateReference

data class EntityReference<T : Any, ID : Any>(
    private val id: ID,
    private val entity: T? = null,
) : AggregateReference<T, ID> {

    override fun getId(): ID = this.id

    val isInitialized: Boolean
        get() = entity != null

    fun unwrap(): T = requireNotNull(entity)

    companion object
}

fun <T : Any, ID : Any> EntityReference.Companion.to(id: ID): EntityReference<T, ID> = EntityReference(id)

fun <T : IdentifiedEntity<ID>, ID : Any> T.asRef(): EntityReference<T, ID> {
    return EntityReference(
        id = requireNotNull(this.id),
        entity = this
    )
}

infix operator fun <T : IdentifiedEntity<ID>, ID : Any> AggregateReference<T, ID>.plus(entity: T): EntityReference<T, ID>  {
    require(this.getId() == entity.id)
    return EntityReference(
        id = requireNotNull(this.id),
        entity = entity
    )
}
