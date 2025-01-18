package ru.netology.coroutines.dto


 data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
)

data class Attachment(
    val url: String,
    val description: String?,
    val type: AttachmentType,
)

enum class AttachmentType {
    IMAGE
}

data class Author(
    val id: Long,
    val name: String,
    val avatar: String,
)


//data class Post(
//    val id: Long,
//    val author: String,
//    val authorAvatar: String,
//    val content: String,
//    val published: Long,
//    val likedByMe: Boolean,
//    val likes: Int = 0,
//)