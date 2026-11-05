package io.averkhoglyad.shortcut.message.core.persistence.entity

import io.averkhoglyad.shortcut.common.persistence.id.IdentifiedEntity
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("chats")
class ChatEntity : IdentifiedEntity<UUID> {

    @Id
    override var id: UUID? = null

    var name: String = ""
    @MappedCollection(idColumn = "chat_id")
    var members: Set<MemberEntity> = emptySet()

    @Column("is_deleted")
    var deleted: Boolean = false

    @Column("last_sync_at")
    var lastSyncAt: Instant = Instant.MIN

}