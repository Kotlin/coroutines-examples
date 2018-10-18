package util

import java.time.*

fun log(msg: String) = println("${Instant.now()} [${Thread.currentThread().name}] $msg")