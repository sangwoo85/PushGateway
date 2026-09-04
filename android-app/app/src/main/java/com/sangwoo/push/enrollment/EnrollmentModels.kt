package com.sangwoo.push.enrollment

import kotlinx.serialization.Serializable

@Serializable
data class EnrollmentTopics(
    val user: String,
    val department: String,
    val notice: String
) {
    fun all(): List<Pair<TopicKind, String>> = listOf(
        TopicKind.USER to user,
        TopicKind.DEPARTMENT to department,
        TopicKind.NOTICE to notice
    )
}

@Serializable
data class EnrollmentQrPayload(
    val version: Int,
    val firebaseProjectId: String,
    val topics: EnrollmentTopics,
    val issuedAt: String,
    val expiresAt: String,
    val nonce: String,
    val signature: String
) {
    fun canonical(): ByteArray = listOf(
        version.toString(), firebaseProjectId, topics.user, topics.department,
        topics.notice, issuedAt, expiresAt, nonce
    ).joinToString("|").toByteArray(Charsets.UTF_8)
}

enum class TopicKind(val displayName: String) {
    USER("개인 알림"),
    DEPARTMENT("부서 알림"),
    NOTICE("전체 공지")
}

sealed class EnrollmentException(message: String) : Exception(message) {
    class InvalidFormat : EnrollmentException("QR 형식이 올바르지 않습니다.")
    class WrongSystem : EnrollmentException("다른 시스템에서 발급한 QR 코드입니다.")
    class InvalidDate : EnrollmentException("QR 코드의 발급 시간을 확인할 수 없습니다.")
    class Expired : EnrollmentException("등록 QR의 유효시간이 만료되었습니다.")
    class IssuedInFuture : EnrollmentException("QR 코드의 발급 시간이 올바르지 않습니다.")
    class InvalidSignature : EnrollmentException("QR 코드의 전자서명을 확인할 수 없습니다.")
    class SubscriptionFailed(kind: TopicKind) : EnrollmentException("${kind.displayName} 구독에 실패했습니다.")
    class ResetFailed : EnrollmentException("기기 등록 초기화에 실패했습니다. 네트워크를 확인해 주세요.")
}
