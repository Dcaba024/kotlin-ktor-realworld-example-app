package io.realworld.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.realworld.app.domain.repository.ArticleTags
import io.realworld.app.domain.repository.Articles
import io.realworld.app.domain.repository.Comments
import io.realworld.app.domain.repository.Favorites
import io.realworld.app.domain.repository.Follows
import io.realworld.app.domain.repository.Tags
import io.realworld.app.domain.repository.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

object DbConfig {
    private var dataSource: HikariDataSource? = null

    fun setup(jdbcUrl: String, username: String, password: String) {
        dataSource?.close()
        val config = HikariConfig().also { config ->
            config.jdbcUrl = jdbcUrl
            config.username = username
            config.password = password
        }
        val newDataSource = HikariDataSource(config)
        dataSource = newDataSource
        TransactionManager.defaultDatabase = Database.connect(newDataSource)
        transaction {
            SchemaUtils.create(Users, Follows, Tags, Articles, ArticleTags, Favorites, Comments)
        }
    }
}
