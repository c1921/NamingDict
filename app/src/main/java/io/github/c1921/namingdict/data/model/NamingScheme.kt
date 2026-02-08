package io.github.c1921.namingdict.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class GivenNameMode {
    Single,
    Double
}

@Serializable
enum class NamingGender {
    Unisex,
    Male,
    Female
}

@Serializable
data class NamingScheme(
    val id: Long = 0L,
    val givenNameMode: GivenNameMode = GivenNameMode.Double,
    val gender: NamingGender = NamingGender.Unisex,
    val slot1: String = "",
    val slot2: String = ""
)
