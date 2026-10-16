package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.ext.slugify
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.Date

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String> = varchar("description", 255)
    val body: Column<String> = text("body")
    val author: Column<EntityID<Long>> = reference("author", Users)
    val createdAt: Column<Instant> = timestamp("created_at")
    val updatedAt: Column<Instant> = timestamp("updated_at")
}

internal object ArticleTags : Table() {
    val article: Column<EntityID<Long>> = reference("article", Articles, onDelete = ReferenceOption.CASCADE)
    val tag: Column<EntityID<Long>> = reference("tag", Tags)
    override val primaryKey = PrimaryKey(article, tag)
}

internal object Favorites : Table() {
    val article: Column<EntityID<Long>> = reference("article", Articles, onDelete = ReferenceOption.CASCADE)
    val user: Column<EntityID<Long>> = reference("user_id", Users)
    override val primaryKey = PrimaryKey(article, user)
}

class ArticleRepository {
    fun findBySlug(slug: String, viewerId: Long?): Article? {
        return transaction {
            Articles.select { Articles.slug eq slug }
                .map { toDomain(it, viewerId) }
                .firstOrNull()
        }
    }

    fun findBy(
        tag: String?,
        author: String?,
        favorited: String?,
        limit: Int,
        offset: Long,
        viewerId: Long?
    ): Pair<List<Article>, Int> {
        return transaction {
            val query = Articles.selectAll()
            if (tag != null) {
                query.andWhere {
                    Articles.id inSubQuery ArticleTags.innerJoin(Tags)
                        .slice(ArticleTags.article)
                        .select { Tags.name eq tag }
                }
            }
            if (author != null) {
                query.andWhere {
                    Articles.author inSubQuery Users.slice(Users.id).select { Users.username eq author }
                }
            }
            if (favorited != null) {
                query.andWhere {
                    Articles.id inSubQuery Favorites.innerJoin(Users)
                        .slice(Favorites.article)
                        .select { Users.username eq favorited }
                }
            }
            page(query, limit, offset, viewerId)
        }
    }

    fun findFeed(viewerId: Long, limit: Int, offset: Long): Pair<List<Article>, Int> {
        return transaction {
            val query = Articles.select {
                Articles.author inSubQuery Follows.slice(Follows.user).select { Follows.follower eq viewerId }
            }
            page(query, limit, offset, viewerId)
        }
    }

    fun create(article: Article, authorId: Long): String {
        return transaction {
            val slug = uniqueSlug(article.title!!)
            val now = Instant.now()
            val articleId = Articles.insertAndGetId { row ->
                row[Articles.slug] = slug
                row[title] = article.title
                row[description] = article.description!!
                row[body] = article.body
                row[author] = EntityID(authorId, Users)
                row[createdAt] = now
                row[updatedAt] = now
            }
            article.tagList.distinct().forEach { name ->
                val tagId = Tags.select { Tags.name eq name }.map { it[Tags.id] }.firstOrNull()
                    ?: Tags.insertAndGetId { row -> row[Tags.name] = name }
                ArticleTags.insert { row ->
                    row[ArticleTags.article] = articleId
                    row[ArticleTags.tag] = tagId
                }
            }
            slug
        }
    }

    fun update(slug: String, article: Article): String? {
        return transaction {
            val current = Articles.select { Articles.slug eq slug }.firstOrNull() ?: return@transaction null
            val newSlug = if (article.title != null && article.title != current[Articles.title]) {
                uniqueSlug(article.title, current[Articles.id])
            } else {
                slug
            }
            Articles.update({ Articles.id eq current[Articles.id] }) { row ->
                row[Articles.slug] = newSlug
                if (article.title != null) {
                    row[title] = article.title
                }
                if (article.description != null) {
                    row[description] = article.description
                }
                row[body] = article.body
                row[updatedAt] = Instant.now()
            }
            newSlug
        }
    }

    fun delete(slug: String) {
        transaction {
            Articles.deleteWhere { Articles.slug eq slug }
        }
    }

    fun favorite(slug: String, userId: Long) {
        transaction {
            val articleId = findId(slug) ?: return@transaction
            val alreadyFavorited = Favorites.select {
                Favorites.article eq articleId and (Favorites.user eq EntityID(userId, Users))
            }.count() > 0
            if (!alreadyFavorited) {
                Favorites.insert { row ->
                    row[article] = articleId
                    row[user] = EntityID(userId, Users)
                }
            }
        }
    }

    fun unfavorite(slug: String, userId: Long) {
        transaction {
            val articleId = findId(slug) ?: return@transaction
            Favorites.deleteWhere {
                Favorites.article eq articleId and (Favorites.user eq EntityID(userId, Users))
            }
        }
    }

    internal fun findId(slug: String): EntityID<Long>? =
        Articles.slice(Articles.id).select { Articles.slug eq slug }.map { it[Articles.id] }.firstOrNull()

    private fun page(query: Query, limit: Int, offset: Long, viewerId: Long?): Pair<List<Article>, Int> {
        val count = query.count().toInt()
        val articles = query.orderBy(Articles.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { toDomain(it, viewerId) }
        return articles to count
    }

    private fun uniqueSlug(title: String, articleId: EntityID<Long>? = null): String {
        val base = title.slugify()
        var slug = base
        var suffix = 1
        while (Articles.select { Articles.slug eq slug }.any { it[Articles.id] != articleId }) {
            suffix++
            slug = "$base-$suffix"
        }
        return slug
    }

    private fun toDomain(row: ResultRow, viewerId: Long?): Article {
        val articleId = row[Articles.id]
        val tags = ArticleTags.innerJoin(Tags)
            .slice(Tags.name)
            .select { ArticleTags.article eq articleId }
            .orderBy(Tags.name)
            .map { it[Tags.name] }
        val favoritesCount = Favorites.select { Favorites.article eq articleId }.count()
        val favorited = viewerId != null && Favorites.select {
            Favorites.article eq articleId and (Favorites.user eq EntityID(viewerId, Users))
        }.count() > 0
        val author = Users.select { Users.id eq row[Articles.author] }
            .map { Users.toDomain(it).copy(password = null) }
            .first()
        return Article(
            slug = row[Articles.slug],
            title = row[Articles.title],
            description = row[Articles.description],
            body = row[Articles.body],
            tagList = tags,
            createdAt = Date.from(row[Articles.createdAt]),
            updatedAt = Date.from(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author
        )
    }
}
