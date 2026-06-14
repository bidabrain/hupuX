package com.hupux.data.model

data class Post(
    val tid: String,
    val title: String,
    val url: String,
    val label: String = "",
    val lights: Int = 0,
    val replies: Int = 0,
    val type: String = "",
    val images: List<String> = emptyList(),
    val isNews: Boolean = false,
    val username: String = "",
    val recommendNum: Int = 0,
    val time: String = "",
    val isVideo: Boolean = false,
    val isVote: Boolean = false
)

data class ZoneCategory(
    val categoryId: Int,
    val name: String,
    val zones: List<Zone>
)

data class Zone(
    val topicId: Int,
    val topicName: String,
    val topicLogo: String,
    val count: String,
    val cateId: Int = 0
)

data class ZoneDetail(
    val topicId: Int,
    val name: String,
    val logo: String,
    val desc: String,
    val followedUserNum: String,
    val allThreadNum: String,
    val bgColor: String
)

data class ZonePage(
    val zoneDetail: ZoneDetail,
    val posts: List<Post>,
    val nextCursor: String?
)

data class PostDetail(
    val tid: Long,
    val title: String,
    val content: String,
    val author: String,
    val authorAvatar: String,
    val authorOrnament: String = "",
    val time: String,
    val views: Int,
    val replies: Int,
    val topicName: String,
    val location: String = "",
    val comments: List<Comment>,
    val hasMoreComments: Boolean,
    val initialNextPid: String? = null
)

data class HotItem(
    val rank: Int,
    val tagId: Long,
    val tagName: String,
    val heat: Long,
    val competitionType: String,
    val icon: String = ""
)

data class UserReply(
    val pid: Long,
    val tid: Long,
    val content: String,
    val lightCount: Int = 0,
    val createTime: Long = 0,
    val threadTitle: String = "",
    val quoteContent: String? = null,
    val quoteUsername: String? = null
)

data class UserReplyPage(
    val replies: List<UserReply>,
    val hasMore: Boolean,
    val maxTime: Long
)

data class UserRecommendPost(
    val tid: Long,
    val title: String,
    val forumName: String = "",
    val topicName: String = "",
    val topicLogo: String = "",
    val replies: Int = 0,
    val lights: Int = 0,
    val recommendNum: Int = 0,
    val createTime: Long = 0,
    val nickname: String = "",
    val summary: String = ""
)

data class UserProfile(
    val uid: String,
    val nickname: String,
    val avatar: String,
    val headerBack: String = "",
    val levelDesc: String = "",
    val levelIcon: String = "",
    val followCount: Int = 0,
    val beFollowCount: Int = 0,
    val postCount: Int = 0,
    val beRecommendCount: Int = 0,
    val beLightCount: Int = 0,
    val location: String = "",
    val regTimeStr: String = "",
    val reputation: Int = 0
)

data class Comment(
    val pid: String,
    val username: String,
    val avatar: String,
    val content: String,
    val lights: Int,
    val replyCount: Int,
    val time: String,
    val location: String,
    val isAuthor: Boolean,
    val quoteUsername: String? = null,
    val quoteContent: String? = null,
    val quotePid: String? = null         // 被引用的父评论 pid
)
