package io.realworld.app.domain.repository

import io.realworld.app.domain.Comment
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.Date

internal object Comments : LongIdTable() {
    val article: Column<EntityID<Long>> = reference("article", Articles, onDelete = ReferenceOption.CASCADE)
    val author: Column<EntityID<Long>> = reference("author", Users)
    val body: Column<String> = text("body")
    val createdAt: Column<Instant> = timestamp("created_at")
    val updatedAt: Column<Instant> = timestamp("updated_at")
}

class CommentRepository(private val articleRepository: ArticleRepository) {
    fun add(slug: String, authorId: Long, body: String): Comment? {
        return transaction {
            val articleId = articleRepository.findId(slug) ?: return@transaction null
            val now = Instant.now()
            val commentId = Comments.insertAndGetId { row ->
                row[article] = articleId
                row[author] = EntityID(authorId, Users)
                row[Comments.body] = body
                row[createdAt] = now
                row[updatedAt] = now
            }
            Comments.select { Comments.id eq commentId }.map { toDomain(it) }.first()
        }
    }

    fun findBySlug(slug: String): List<Comment>? {
        return transaction {
            val articleId = articleRepository.findId(slug) ?: return@transaction null
            Comments.select { Comments.article eq articleId }
                .orderBy(Comments.createdAt, SortOrder.DESC)
                .map { toDomain(it) }
        }
    }

    fun findById(slug: String, id: Long): Comment? {
        return transaction {
            val articleId = articleRepository.findId(slug) ?: return@transaction null
            Comments.select { Comments.id eq id and (Comments.article eq articleId) }
                .map { toDomain(it) }
                .firstOrNull()
        }
    }

    fun delete(id: Long) {
        transaction {
            Comments.deleteWhere { Comments.id eq id }
        }
    }

    private fun toDomain(row: ResultRow): Comment {
        val author = Users.select { Users.id eq row[Comments.author] }
            .map { Users.toDomain(it).copy(password = null) }
            .first()
        return Comment(
            id = row[Comments.id].value,
            createdAt = Date.from(row[Comments.createdAt]),
            updatedAt = Date.from(row[Comments.updatedAt]),
            body = row[Comments.body],
            author = author
        )
    }
}
