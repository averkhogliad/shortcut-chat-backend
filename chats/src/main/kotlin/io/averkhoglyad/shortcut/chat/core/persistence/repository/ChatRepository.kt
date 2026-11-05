package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.*

interface ChatRepository : Repository<ChatEntity, UUID> {

    fun findById(id: UUID): ChatEntity?

    fun save(chat: ChatEntity): ChatEntity

    @Query("select distinct c.* from chats c inner join chat_members m on m.chat_id=c.id where m.user_id=:userId")
    fun findByMembersContainsOrderById(@Param("userId") userId: UUID,
                                       pageable: Pageable): List<ChatEntity>

    @Query("select distinct c.* from chats c inner join chat_members m on m.chat_id=c.id where m.user_id=:userId and c.id>:chatId")
    fun findByMembersContainsAndIdGreaterThanOrderById(@Param("userId") userId: UUID,
                                                       @Param("chatId") chatId: UUID,
                                                       pageable: Pageable): List<ChatEntity>

    @Query(
        "select m.* from chat_members m " +
            "where m.chat_id IN (:chatIds) " +
            "and (:includeDeleted = true or not exists (" +
            "select 1 from users u where u.id = m.user_id and u.is_deleted)) " +
            "order by m.chat_id, m.user_id"
    )
    fun findMembersByChatIdIsIn(@Param("chatIds") chatIds: Collection<UUID>,
                                @Param("includeDeleted") includeDeleted: Boolean): List<MemberEntity>
}