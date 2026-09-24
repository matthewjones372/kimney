package example.cookbook.crossing

import io.github.matthewjones372.kimney.into

data class Article(val title: String, val tags: Set<String>)

data class ArticleDto(val title: String, val tags: List<String>)

fun toDto(article: Article): ArticleDto = article.into<_, ArticleDto>()
    .withFieldComputed(ArticleDto::tags) { it.tags.sorted() }
    .transform()
