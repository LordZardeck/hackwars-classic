package com.hackwars.rewrite.client.auth

import com.hackwars.rewrite.client.mvc.RewriteViewModel

data class RewriteLoginViewModel(
    val showForm: Boolean = false,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
) : RewriteViewModel

data class RewriteLoginCredentials(
    val email: String,
    val password: CharArray,
)
