package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.exceptions.ForbiddenException
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository

class ArticleService(private val articleRepository: ArticleRepository, private val userRepository: UserRepository) {
    fun findBy(tag: String?, author: String?, favorited: String?, limit: Int, offset: Long, email: String?): ArticlesDTO {
        return articleRepository.findBy(tag, author, favorited, limit, offset, findUserId(email)).let { (articles, count) ->
            ArticlesDTO(articles, count)
        }
    }

    fun findFeed(email: String, limit: Int, offset: Long): ArticlesDTO {
        return articleRepository.findFeed(getUserId(email), limit, offset).let { (articles, count) ->
            ArticlesDTO(articles, count)
        }
    }

    fun findBySlug(slug: String, email: String?): Article {
        return articleRepository.findBySlug(slug, findUserId(email))
            ?: throw NotFoundException("Article not found.")
    }

    fun create(email: String, article: Article): Article {
        require(!article.title.isNullOrBlank() && !article.description.isNullOrBlank() && article.body.isNotBlank()) {
            "Article title, description and body are required."
        }
        val userId = getUserId(email)
        val slug = articleRepository.create(article, userId)
        return articleRepository.findBySlug(slug, userId)!!
    }

    fun update(email: String, slug: String, article: Article): Article {
        val userId = checkAuthor(email, slug)
        val newSlug = articleRepository.update(slug, article) ?: throw NotFoundException("Article not found.")
        return articleRepository.findBySlug(newSlug, userId)!!
    }

    fun delete(email: String, slug: String) {
        checkAuthor(email, slug)
        articleRepository.delete(slug)
    }

    fun favorite(email: String, slug: String): Article {
        val userId = getUserId(email)
        findBySlug(slug, email)
        articleRepository.favorite(slug, userId)
        return articleRepository.findBySlug(slug, userId)!!
    }

    fun unfavorite(email: String, slug: String): Article {
        val userId = getUserId(email)
        findBySlug(slug, email)
        articleRepository.unfavorite(slug, userId)
        return articleRepository.findBySlug(slug, userId)!!
    }

    private fun checkAuthor(email: String, slug: String): Long {
        val userId = getUserId(email)
        val article = findBySlug(slug, email)
        if (article.author?.id != userId) {
            throw ForbiddenException("Only the author can change this article.")
        }
        return userId
    }

    private fun findUserId(email: String?): Long? = email?.let { userRepository.findByEmail(it)?.id }

    private fun getUserId(email: String): Long = findUserId(email) ?: throw NotFoundException("User not found.")
}
