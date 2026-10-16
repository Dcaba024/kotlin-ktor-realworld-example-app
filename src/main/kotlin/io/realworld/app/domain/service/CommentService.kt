package io.realworld.app.domain.service

import io.realworld.app.domain.Comment
import io.realworld.app.domain.exceptions.ForbiddenException
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.CommentRepository
import io.realworld.app.domain.repository.UserRepository

class CommentService(private val commentRepository: CommentRepository, private val userRepository: UserRepository) {
    fun add(slug: String, email: String, comment: Comment): Comment {
        require(comment.body.isNotBlank()) { "Comment body is required." }
        val userId = getUserId(email)
        return commentRepository.add(slug, userId, comment.body) ?: throw NotFoundException("Article not found.")
    }

    fun findBySlug(slug: String): List<Comment> {
        return commentRepository.findBySlug(slug) ?: throw NotFoundException("Article not found.")
    }

    fun delete(email: String, slug: String, id: Long) {
        val comment = commentRepository.findById(slug, id) ?: throw NotFoundException("Comment not found.")
        if (comment.author?.id != getUserId(email)) {
            throw ForbiddenException("Only the author can delete this comment.")
        }
        commentRepository.delete(id)
    }

    private fun getUserId(email: String): Long =
        userRepository.findByEmail(email)?.id ?: throw NotFoundException("User not found.")
}
