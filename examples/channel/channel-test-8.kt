package channel.test8

import channel.SendChannel
import channel.go
import channel.suspending
import java.util.*

// https://tour.golang.org/concurrency/7
// https://tour.golang.org/concurrency/8

//suspend fun Tree.walk(ch: SendChannel<Int>) = suspending {
//    left?.walk(ch)
//    ch.send(value)
//    right?.walk(ch)
//    Unit
//}

//suspend fun same(t1: Tree, t2: Tree): Boolean = suspending same@ {
//    val c1 = Channel<Int>()
//    val c2 = Channel<Int>()
//    t1.walk(c1)
//    t2.walk(c2)
//
//    true
//}

fun main(args: Array<String>) = go {
    val t1 = newTree(1)
    val t2 = newTree(2)
    println("t1 = $t1")
    println("t2 = $t2")
}

// https://github.com/golang/tour/blob/master/tree/tree.go

data class Tree(val value: Int, val left: Tree? = null, val right: Tree? = null)

fun Tree?.insert(v: Int): Tree {
    if (this == null) return Tree(v)
    if (v < value)
        return Tree(value, left.insert(v), right)
    else
        return Tree(value, left, right.insert(v))
}

fun newTree(k: Int): Tree {
    var t: Tree? = null
    val list = (1..10).toMutableList()
    Collections.shuffle(list)
    for (v in list) {
        t = t.insert(v * k)
    }
    return t!!
}
