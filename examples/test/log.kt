package test

import java.time.Instant

fun log(msg: String) = println("${Instant.now()} [${Thread.currentThread().name}] $msg")