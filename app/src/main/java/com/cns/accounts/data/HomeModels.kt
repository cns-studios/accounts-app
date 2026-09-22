package com.cns.accounts.data

import androidx.compose.runtime.Immutable

@Immutable
data class Profile(
    val name: String,
    val twoFactorLabel: String,
)

@Immutable
data class SignInRequest(
    val device: String,
    val timeLabel: String,
)

@Immutable
data class Account(
    val label: String,
    val code: String,
    val secretLength: Int,
)

@Immutable
data class Service(
    val name: String,
    val status: String,
)

data class HomeData(
    val profile: Profile = Profile("aaron", "2FA enabled"),
    val signInRequest: SignInRequest = SignInRequest("Firefox on Linux", "now"),
    val account: Account = Account("Account", randomAccountCode(), 17),
    val services: List<Service> = listOf(Service("Sendly", "now")),
)

fun sampleHomeData() = HomeData()

fun randomAccountCode(): String {
    fun group() = (0..999).random().toString().padStart(3, '0')
    return "${group()}-${group()}"
}
