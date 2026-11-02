package example.cookbook.trees

import dev.surefold.kimney.transformInto

data class Comment(val author: String, val text: String, val replies: List<Comment>)

data class CommentView(val author: String, val text: String, val replies: List<CommentView>)

fun Comment.toView(): CommentView = transformInto()
