package io.averkhoglyad.shortcut.message.api.controller

import io.averkhoglyad.shortcut.common.security.SecurityService
import io.averkhoglyad.shortcut.common.web.handler.unwrap
import io.averkhoglyad.shortcut.message.api.data.SliceResponse
import io.averkhoglyad.shortcut.message.core.model.Message
import io.averkhoglyad.shortcut.message.core.model.MessageRequest
import io.averkhoglyad.shortcut.message.core.model.UserRef
import io.averkhoglyad.shortcut.message.core.service.MessageService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("messages")
class MessageController(
    private val service: MessageService,
    private val securityService: SecurityService,
) {

    @GetMapping(params = ["!lastId"])
    fun list(
        @RequestParam("chat") chatId: UUID,
        @RequestParam("pageSize") pageSize: Int = 10,
    ): SliceResponse<Message> {
        return service.listForChat(chatId, pageSize)
            .asSliceResponse()
    }

    @GetMapping(params = ["lastId"])
    fun list(
        @RequestParam("chat") chatId: UUID,
        @RequestParam("pageSize") pageSize: Int = 10,
        @RequestParam("lastId") lastId: UUID
    ): SliceResponse<Message> {
        return service.listForChat(chatId, lastId, pageSize)
            .asSliceResponse()
    }

    @PostMapping
    fun create(@RequestBody message: MessageRequest): Message {
        return service.create(message, UserRef(securityService.currentUserId()))
            .unwrap()
    }
}

private fun List<Message>.asSliceResponse(): SliceResponse<Message> =
    SliceResponse(
        data = this,
        next = this.lastOrNull()?.id?.toString()
    )