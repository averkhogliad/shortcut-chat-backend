package io.averkhoglyad.shortcut.users.api.controller

import io.averkhoglyad.shortcut.common.security.SecurityService
import io.averkhoglyad.shortcut.common.web.handler.unwrap
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/users")
class UserController(
    private val service: UserService,
    private val securityService: SecurityService,
) {

    @GetMapping("/{id}")
    fun find(@PathVariable id: UUID): User {
        return service.find(id)
            .unwrap()
    }

    @GetMapping("/by-email")
    fun find(@RequestParam email: String): User {
        return service.findByEmail(email)
            .unwrap()
    }

    @PostMapping
    fun create(@RequestBody user: User): User {
        require(user.id == null)
        return service.create(user)
            .unwrap()
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody user: User): User {
        require(user.id == null || user.id == id)
        return service.update(user.copy(id = id))
            .unwrap()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Unit> {
        return service.delete(id)
            .unwrap()
            .let { ResponseEntity.noContent().build() }
    }

    @GetMapping("/me")
    fun findMe(): User {
        return service.find(securityService.currentUserId())
            .unwrap()
    }

    @PutMapping("/me")
    fun updateMe(@RequestBody user: User): User {
        val id = securityService.currentUserId()
        require(user.id == null || user.id == id)
        return service.update(user.copy(id = id))
            .unwrap()
    }

    @DeleteMapping("/me")
    fun deleteMe(): ResponseEntity<Unit> {
        return service.delete(securityService.currentUserId())
            .unwrap()
            .let { ResponseEntity.noContent().build() }
    }
}
