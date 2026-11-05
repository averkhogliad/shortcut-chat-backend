package io.averkhoglyad.shortcut.chat.api.controller

import io.averkhoglyad.shortcut.chat.core.model.ChatMembers
import io.averkhoglyad.shortcut.chat.core.service.ChatService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/members")
class ChatMembersController(
    private val service: ChatService
) {

    @GetMapping
    fun members(
        @RequestParam(name = "chatId", required = false) chatIds: Collection<UUID> = emptyList(),
        @RequestParam(name = "includeDeleted", required = false, defaultValue = "false") includeDeleted: Boolean,
    ): Collection<ChatMembers> {
        return takeIf { chatIds.isNotEmpty() }
            ?.let{ service.findMembersByIds(chatIds, includeDeleted) }
            ?: emptyList()
    }
}