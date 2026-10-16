package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.domain.service.CommentService

class CommentController(private val commentService: CommentService) {
    suspend fun add(ctx: ApplicationCall) {
        val comment = ctx.receive<CommentDTO>().comment
        requireNotNull(comment) { "Comment is required." }
        commentService.add(ctx.slug(), ctx.requireUserEmail(), comment).also { added ->
            ctx.respond(CommentDTO(added))
        }
    }

    suspend fun findBySlug(ctx: ApplicationCall) {
        commentService.findBySlug(ctx.slug()).also { comments ->
            ctx.respond(CommentsDTO(comments))
        }
    }

    suspend fun delete(ctx: ApplicationCall) {
        val id = requireNotNull(ctx.parameters["id"]?.toLongOrNull()) { "Comment id is invalid." }
        commentService.delete(ctx.requireUserEmail(), ctx.slug(), id)
        ctx.respond(HttpStatusCode.OK)
    }
}
