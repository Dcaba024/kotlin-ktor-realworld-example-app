package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.response.respond
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.domain.service.UserService

class ProfileController(private val userService: UserService) {
    suspend fun get(ctx: ApplicationCall) {
        userService.getProfileByUsername(ctx.userEmail(), ctx.username()).also { profile ->
            ctx.respond(ProfileDTO(profile))
        }
    }

    suspend fun follow(ctx: ApplicationCall) {
        userService.follow(ctx.requireUserEmail(), ctx.username()).also { profile ->
            ctx.respond(ProfileDTO(profile))
        }
    }

    suspend fun unfollow(ctx: ApplicationCall) {
        userService.unfollow(ctx.requireUserEmail(), ctx.username()).also { profile ->
            ctx.respond(ProfileDTO(profile))
        }
    }

    private fun ApplicationCall.username(): String =
        requireNotNull(parameters["username"]) { "Username is required." }
}
