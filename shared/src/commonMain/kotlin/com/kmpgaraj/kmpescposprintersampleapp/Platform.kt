package com.kmpgaraj.kmpescposprintersampleapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform