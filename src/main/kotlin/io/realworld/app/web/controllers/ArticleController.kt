package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {
    suspend fun findBy(ctx: ApplicationCall) {
        val tag = ctx.parameters["tag"]
        val author = ctx.parameters["author"]
        val favorited = ctx.parameters["favorited"]
        val limit = ctx.parameters["limit"]?.toIntOrNull() ?: 20
        val offset = ctx.parameters["offset"]?.toLongOrNull() ?: 0
        articleService.findBy(tag, author, favorited, limit, offset, ctx.userEmail()).also { articles ->
            ctx.respond(articles)
        }
    }

    suspend fun feed(ctx: ApplicationCall) {
        val limit = ctx.parameters["limit"]?.toIntOrNull() ?: 20
        val offset = ctx.parameters["offset"]?.toLongOrNull() ?: 0
        articleService.findFeed(ctx.requireUserEmail(), limit, offset).also { articles ->
            ctx.respond(articles)
        }
    }

    suspend fun get(ctx: ApplicationCall) {
        articleService.findBySlug(ctx.slug(), ctx.userEmail()).also { article ->
            ctx.respond(ArticleDTO(article))
        }
    }

    suspend fun create(ctx: ApplicationCall) {
        val article = ctx.receive<ArticleDTO>().article
        requireNotNull(article) { "Article is required." }
        articleService.create(ctx.requireUserEmail(), article).also { created ->
            ctx.respond(ArticleDTO(created))
        }
    }

    suspend fun update(ctx: ApplicationCall) {
        val article = ctx.receive<ArticleDTO>().article
        requireNotNull(article) { "Article is required." }
        articleService.update(ctx.requireUserEmail(), ctx.slug(), article).also { updated ->
            ctx.respond(ArticleDTO(updated))
        }
    }

    suspend fun delete(ctx: ApplicationCall) {
        articleService.delete(ctx.requireUserEmail(), ctx.slug())
        ctx.respond(HttpStatusCode.OK)
    }

    suspend fun favorite(ctx: ApplicationCall) {
        articleService.favorite(ctx.requireUserEmail(), ctx.slug()).also { article ->
            ctx.respond(ArticleDTO(article))
        }
    }

    suspend fun unfavorite(ctx: ApplicationCall) {
        articleService.unfavorite(ctx.requireUserEmail(), ctx.slug()).also { article ->
            ctx.respond(ArticleDTO(article))
        }
    }
}

internal fun ApplicationCall.userEmail(): String? = authentication.principal<User>()?.email

internal fun ApplicationCall.requireUserEmail(): String {
    val email = userEmail()
    require(!email.isNullOrBlank()) { "User not logged." }
    return email
}

internal fun ApplicationCall.slug(): String = requireNotNull(parameters["slug"]) { "Slug is required." }
