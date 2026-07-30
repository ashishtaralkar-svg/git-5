package com.hdfc.docupload.domain.model

/**
 * A generic wrapper describing the state of an operation that talks to a
 * data source (network, database, etc.).
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>
}
