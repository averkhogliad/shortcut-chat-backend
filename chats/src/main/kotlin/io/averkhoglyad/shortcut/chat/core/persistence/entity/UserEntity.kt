package io.averkhoglyad.shortcut.chat.core.persistence.entity

import io.averkhoglyad.shortcut.common.persistence.id.IdentifiedEntity
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.InsertOnlyProperty
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("users")
class UserEntity : IdentifiedEntity<UUID> {

    @Id
    override var id: UUID? = null

    @InsertOnlyProperty
    @Column("external_id")
    var externalId: String = ""
    var name: String = ""
    var email: String = ""

    @Column("is_deleted")
    var deleted: Boolean = false

    @Column("last_sync_at")
    var lastSyncAt: Instant = Instant.MIN

}
