package com.attiqtherehman.kharcha.domain.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val icon: String,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
