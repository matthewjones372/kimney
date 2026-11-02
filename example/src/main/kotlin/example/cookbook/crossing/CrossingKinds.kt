package example.cookbook.crossing

import dev.surefold.kimney.into

data class Article(val title: String, val tags: Set<String>)

data class ArticleDto(val title: String, val tags: List<String>)

fun Article.toDto(): ArticleDto = into<_, ArticleDto>()
    .withFieldComputed(ArticleDto::tags) { it.tags.sorted() }
    .transform()
