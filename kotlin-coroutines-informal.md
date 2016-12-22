# Coroutines for Kotlin (Revision 2)

* **Type**: Informal description
* **Author**: Andrey Breslav
* **Contributors**: Vladimir Reshetnikov, Stanislav Erokhin, Ilya Ryzhenkov, Denis Zharkov, Roman Elizarov
* **Status**: Implemented in 1.1-M04

## Abstract

This is a description of coroutines in Kotlin. This concept is also known as, or partly covers

- generators/yield
- async/await
- composable сontinuations

Goals:

- No dependency on a particular implementation of Futures or other such rich library;
- Cover equally the "async/await" use case and "generator blocks";
- Make it possible to utilize Kotlin coroutines as wrappers for different existing asynchronous APIs 
  (such as Java NIO, different implementations of Futures, etc).

## Table of Contents

* [Use cases](#use-cases)
  * [Asynchronous computations](#asynchronous-computations)
  * [Futures](#futures)
  * [Generators](#generators)
  * [Asynchronous UI](#asynchronous-ui)
  * [More use cases](#more-use-cases)
* [Coroutines overview](#coroutines-overview)
  * [Terminology](#terminology)
  * [Continuation interface](#continuation-interface)
  * [Suspending functions](#suspending-functions)
  * [Coroutine builders](#coroutine-builders)
  * [Dispatcher](#dispatcher)
  * [Restricted suspension](#restricted-suspension)
* [More examples](#more-examples)
  * [Wrapping callbacks](#wrapping-callbacks)
  * [Cooperative single-thread multitasking](#cooperative-single-thread-multitasking)
  * [Asynchronous sequences](#asynchronous-sequences)
  * [Channels](#channels)
  * [Mutexes](#mutexes)
* [Advanced topics](#advanced-topics)
  * [Resource management and GC](#resource-management-and-gc)
  * [Concurrency and threads](#concurrency-and-threads)
  * [Asynchronous programming styles](#asynchronous-programming-styles)
* [Implementation details](#implementation-details)
  * [Continuation passing style](#continuation-passing-style)
  * [State machines](#state-machines)
  * [Coroutine intrinsics](#coroutine-intrinsics)

## Use cases

A coroutine can be thought of as an instance of _suspendable computation_, i.e. the one that can suspend at some 
points and later resume execution possibly on another thread. Coroutines calling each other 
(and passing data back and forth) can form 
the machinery for cooperative multitasking.
 
### Asynchronous computations 
 
The first class of motivating use cases for coroutines are asynchronous computations 
(handled by async/await in C# and other languages). 
Let's take a look at how such computations are done with callbacks. As an inspiration, let's take 
asynchronous I/O (the APIs below are simplified):

```kotlin
// asynchronously read into `buf`, and when done run the lambda
inFile.read(buf) {
    // this lambda is executed when the reading completes
    bytesRead ->
    ...
    ...
    process(buf, bytesRead)
    
    // asynchronously write from `buf`, and when done run the lambda
    outFile.write(buf) {
        // this lambda is executed when the writing completes
        ...
        ...
        outFile.close()          
    }
}
```

Note that we have a callback inside a callback here, and while it saves us from a lot of boilerplate (e.g. there's no 
need to pass the `buf` parameter explicitly to callbacks, they just see it as a part of their closure), the indentation
levels are growing every time, and one can easily anticipate the problems that may come at nesting levels greater 
than one (google for "callback hell" to see how much people suffer from this in JavaScript).

This same computation can be expressed straightforwardly as a coroutine (provided that there's a library that adapts
the I/O APIs to coroutine requirements):
 
```kotlin
async {
    // suspend while asynchronously reading
    val bytesRead = inFile.aRead(buf) 
    // we only get to this line when reading completes
    ...
    ...
    process(buf, bytesRead)
    // suspend while asynchronously writing   
    outFile.aWrite(buf)
    // we only get to this line when writing completes  
    ...
    ...
    outFile.close()
}
```

The `aRead()` and `aWrite()` here are special _suspending functions_ — they _suspend_ execution 
(which does not mean blocking the thread it has been running on) and _resume_ when the call has completed. 
If we squint our eyes just enough to imagine that all the code after `aRead()` has been wrapped in a 
lambda and passed to `aRead()` as a callback, and the same has been done for `aWrite()`, 
we can see that this code is the same as above, only more readable. 

It is our explicit goal to support coroutines in a very generic way, so in this example,
 `async {}`, `File.aRead()`, and `File.aWrite()` are just **library functions** geared for 
working with coroutines (details below): `async` is the _coroutine builder_ — it marks 
the _scope_ of a coroutine and starts it, while `aRead/aWrite` are special 
_suspending functions_ which implicitly receive 
_continuations_ (continuations are just generic callbacks).  

> The library code for `async{}` is shown in [coroutine builders](#coroutine-builders) section, and
the library code for `File.aRead()` is shown in [wrapping callbacks](#wrapping-callbacks) section.

Note, that with explicitly passed callbacks having an asynchronous call in the middle of a loop can be tricky, 
but in a coroutine it is a perfectly normal thing to have:

```kotlin
async {
    while (true) {
        // suspend while asynchronously reading
        val bytesRead = inFile.aRead(buf)
        // continue when the reading is done
        if (bytesRead == -1) break
        ...
        process(buf, bytesRead)
        // suspend while asynchronously writing
        outFile.aWrite(buf) 
        // continue when the writing is done
        ...
    }
}
```

One can imagine that handling exceptions is also a bit more convenient in a coroutine.

### Futures

There's another style of expressing asynchronous computations: through futures (and their close relatives — promises).
We'll use an imaginary API here, to apply an overlay to an image:

```kotlin
val future = runAfterBoth(
    asyncLoadImage("...original..."), // creates a Future 
    asyncLoadImage("...overlay...")   // creates a Future
) {
    original, overlay ->
    ...
    applyOverlay(original, overlay)
}
return future.get()
```

With coroutines, this could be rewritten as

```kotlin
async {
    val original = asyncLoadImage("...original...") // creates a Future
    val overlay = asyncLoadImage("...overlay...")   // creates a Future
    ...
    // suspend while awaiting the loading of the images
    // then run `applyOverlay(...)` when they are both loaded
    return applyOverlay(await(original), await(overlay))
}
```

> The library code for `async{}` is shown in [coroutine builders](#coroutine-builders) section, and
the library code for `await()` is shown in [suspending functions](#suspending-functions) section.

Again, less indentation and more natural composition logic (and exception handling, not shown here), 
and no building async/await into the language: `async{}` and `await()` are functions in a library. 

### Generators

Another typical use case for coroutines would be lazily computed sequences (handled by `yield` in C#, Python 
and many other languages). Such a sequence can be generated by seemingly sequential code, but at runtime only 
requested elements are computed:

```kotlin
// inferred type is Sequence<Int>
val fibonacci = generate {
    yield(1) // first Fibonacci number
    var cur = 1
    var next = 1
    while (true) {
        yield(next) // next Fibonacci number
        val tmp = cur + next
        cur = next
        next = tmp
    }
}
```

This code creates a lazy `Sequence` of [Fibonacci numbers](https://en.wikipedia.org/wiki/Fibonacci_number), 
that is potentially infinite 
(exactly like [Haskel's infinite lists](http://www.techrepublic.com/article/infinite-list-tricks-in-haskell/)). 
We can request some of it, for example, through `take()`:
 
```kotlin
println(fibonacci.take(10).joinToString())
```

> This will print `1, 1, 2, 3, 5, 8, 13, 21, 34, 55`
  You can try this code [here](examples/fibonacci.kt)  
 
The strength of generators is in supporting arbitrary control flow, such as `while` (from the example above),
`if`, `try`/`catch`/`finally` and everything else: 
 
```kotlin
val seq = generate {
    yield(firstItem) // suspension point

    for (item in input) {
        if (!item.isValid()) break // don't generate any more items
        val foo = item.toFoo()
        if (!foo.isGood()) continue
        yield(foo) // suspension point        
    }
    
    try {
        yield(lastItem()) // suspension point
    }
    finally {
        // some finalization code
    }
} 
```

> The library code for `generate {}` and `yield()` is shown in 
[restricted suspension](#restricted-suspension) section.

Note that this approach also allows to express `yieldAll(sequence)` as a library function 
(as well as `generate{}` and `yield()` are), which simplifies joining lazy sequences and allows 
for efficient implementation.

### Asynchronous UI

A typical UI application has a single event dispatch thread where all UI operations happen. 
Modification of UI state from other threads is usually not allowed. All UI libraries provide
some kind of primitive to move execution back to UI thread. Swing, for example, has 
[`SwingUtilities.invokeLater`](https://docs.oracle.com/javase/8/docs/api/javax/swing/SwingUtilities.html#invokeLater-java.lang.Runnable-),
JavaFX has 
[`Platform.runLater`](https://docs.oracle.com/javase/8/javafx/api/javafx/application/Platform.html#runLater-java.lang.Runnable-), 
Android has
[`Activity.runOnUiThread`](https://developer.android.com/reference/android/app/Activity.html#runOnUiThread(java.lang.Runnable)),
etc.
Here is a snippet of code from a typical Swing application that does some asynchronous
operation and then displays its result in the UI:

``` kolin
makeAsyncRequest {
    // this lambda is executed when the async request completes
    result, exception ->
    
    if (exception == null) {
        // display result in UI
        SwingUtilities.invokeLater {
            display(result)   
        }
    } else {
       // process exception
    }
}
```

This is similar to callback hell that we've seen in [asynchronous computations](#asynchronous-computations) use case
and it is elegantly solved by coroutines, too:
 
```
asyncSwing {
    try {
        // suspend while asynchronously making request
        val result = makeRequest()
        // display result in UI, here asyncSwing ensures that we always stay in event dispatch thread
        display(result)
    } catch (exception: Throwable) {
       // process exception        
    }
}
```
 
 > The library code for `asyncSwing {}` is shown in the [dispatcher](#dispatcher) section.
 
All exception handling is performed using natural language constructs. 

### More use cases
 
Coroutines can cover many more use cases, including these:  
 
* Channel-based concurrency (aka goroutines and channels);
* Actor-based concurrency;
* Background processes occasionally requiring user interaction, e.g., show a modal dialog;
* Communication protocols: implement each actor as a sequence rather than a state machine;
* Web application workflows: register a user, validate email, log them in 
(a suspended coroutine may be serialized and stored in a DB).

## Coroutines overview

This section gives an overview of the language mechanisms that enable writing coroutines and 
the standard libraries that govern their semantics.  

### Terminology

* A _coroutine_ — is an _instance_ of _suspendable computation_. It is conceptually similar to a thread, in the sense that
it takes a block of code to run and has a similar life-cycle — it is _created_ and _started_, but it is not bound
to any particular thread. It may _suspend_ its execution in one thread and _resume_ in another one. 
Moreover, like a future or promise, it may _complete_ with some result or exception.
 
* A _suspending function_ — a function that is marked with `suspend` modifier. It may _suspend_ execution of the code
  without blocking the current thread of execution by invoking other suspending functions. A suspending function 
  cannot be invoked from a regular code, but only from other suspending functions and from suspending lambdas (see below).
  For example, `await()` and `yield()`, as shown in [use cases](#use-cases), are suspending functions that may 
  be defined in a library. The standard library provides primitive suspending functions that are used to define 
  all other suspending functions.
  
> Note: In the current version suspending functions are only allowed to make _tail-calls_ to other suspending functions.
  This restriction will be lifted in the future.

* A _suspending lambda_ — a block of code that can be run in a coroutine.
It looks exactly like an ordinary [lambda expression](https://kotlinlang.org/docs/reference/lambdas.html)
but its functional type is marked with `suspend` modifier. 
Just like a regular lambda expression is a short syntactic form for an anonymous local function,
a suspending lambda is a short syntactic form for an anonymous suspending function. It may _suspend_ execution of the code
without blocking the current thread of execution by invoking suspending functions.
For example, the block of code in curly braces following `async` function, as shown in [use cases](#use-cases), 
is a suspending lambda.

> Note: Suspending lambdas may invoke suspending functions in all places of their code where a 
  [non-local](https://kotlinlang.org/docs/reference/returns.html) `return` statement
  from this lambda is allowed. That is, suspending function calls inside inline lambdas 
  like [`apply{}` block](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/apply.html) are allowed,
  but not in the non-inline nor in `crossinline` inner lambda expressions. 
  A _suspension_ is treated as a special kind of non-local control transfer.

* A _suspending function type_ — is a function type for suspending functions and lambdas. It is just like 
  a regular [function type](https://kotlinlang.org/docs/reference/lambdas.html#function-types), 
  but with `suspend` modifier. For example, `suspend () -> Int` is a type of suspending
  function without arguments that returns `Int`. A suspending function that is declared like `suspend fun foo(): Int`
  conforms to this function type.

* A _coroutine builder_ — a function that takes some _suspending lambda_ as an argument, creates a coroutine,
and gives access to its result in some form. For example, `async{}` and `generate{}`, 
as shown in [use cases](#use-cases), are coroutine builders defined in a library.
The standard library provides primitive coroutine builders that are used to define all other coroutine builders.

> Note: Some languages have hard-coded support for particular ways to create and start a coroutines that define
  how their execution and result are represented. For example, `generate` _keyword_ may define a coroutine that 
  returns a certain kind of iterable object, while `async` _keyword_ may define a coroutine that returns a
  certain kind of promise or task. Kotlin does not have keywords or modifiers to define and start a coroutine. 
  Coroutine builders are simply functions defined in a library. 
  In case where a coroutine definition takes the form of a method body in another language, 
  in Kotlin such method would typically be a regular method with an expression body, 
  consisting of an invocation of some library-defined coroutine builder whose last argument is a suspending lambda:
 
 ```kotlin
 fun asyncTask() = async { ... }
 ```

* A _suspension point_ — is a point during coroutine execution where the execution of the coroutine _may be suspended_. 
Syntactically, a suspension point is an invocation of suspending function, but the actual
suspension happens when the suspending function invokes the standard library primitive to suspend the execution.

* A _continuation_ — is a state of the suspended coroutine at suspension point. It conceptually represents 
the rest of its execution after the suspension point. For example:

```kotlin
generate {
    for (i in 1..10) yield(i * i)
    println("over")
}  
```  

Here, every time the coroutine is suspended at a call to suspending function `yield()`, 
_the rest of its execution_ is represented as a continuation, so we have 10 continuations: 
first runs the loop with `i = 2` and suspends, second runs the loop with `i = 3` and suspends, etc, 
the last one prints "over" and completes the coroutine. The coroutine that is _created_, but is not 
_started_ yet, is represented by its _initial continuation_ of type `Continuation<Unit>` that consists of
its whole execution.

As mentioned above, one of the driving requirements for coroutines is flexibility:
we want to be able to support many existing asynchronous APIs and other use cases and minimize 
the parts hard-coded into the compiler. As a result, the compiler is only responsible for support
of suspending functions, suspending lambdas, and the corresponding suspending function types. 
There are few primitives in the standard library and the rest is left to application libraries. 

### Continuation interface

Here is the definition of the standard library interface `Continuation`, which represents
a generic callback:

```kotlin
interface Continuation<in T> {
   fun resume(value: T)
   fun resumeWithException(exception: Throwable)
}
```

### Suspending functions

An implementation of a typical _suspending function_ like `await` looks like this:
  
```kotlin
suspend fun <T> await(f: CompletableFuture<T>): T =
    suspendCoroutine<T> { c: Continuation<T> ->
        f.whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                c.resume(result) 
            else // the future has completed with an exception
                c.resumeWithException(exception)
        }
    }
``` 

> You can get this code [here](examples/await.kt)  

The `suspend` modifier indicates that this is a function that can suspend execution of a coroutine. 
Suspending functions may invoke any regular functions, but to actually suspend execution they must
invoke some other suspending function. In particular, this `await` implementation invokes a suspending function 
`suspendCoroutine` that is defined in the standard library in the following way:

```kotlin
suspend fun <T> suspendCoroutine(block: (Continuation<T>) -> Unit): T
```

When `suspendCoroutine` is called inside a coroutine (and it can _only_ be called inside
a coroutine, because it is a suspending function) it _suspends_ the execution of coroutine, captures its
state in a _continuation_ instance and passes this continuation to the specified `block` as an argument.
To resume execution of the coroutine, the block may call either `contination.resume()` or
`continuation.resumeWithException()` in this thread of in some other thread. 
Resuming the same continuation more than once is not allowed and produces `IllegalStateException`.

> Note: That is the key difference between coroutines in Kotlin and first-class delimited continuations in 
functional languages like Scheme or continuation monad in Haskel. The choice to support only limited resume-once 
continuations is purely pragmatic as none of the intended [uses cases](#use-cases) need first-class continuations 
and we can more efficiently implement limited version of them. However, first-class continuations can be 
implemented as a separate library by cloning the state of the coroutine that is
captured in continuation, so that its clone can be resumed again. This mechanism may be efficiently 
provided by the standard library in the future.

The value passed to `continuation.resume()` becomes the **return value** of `suspendCoroutine()`,
which, in turn, becomes the return value of `await()` when the coroutine _resumes_ its execution.

### Coroutine builders

Suspending functions cannot be invoked from regular functions, so the standard library provides functions
to start coroutine execution from a regular non-suspending scope. Here is the implementation of a typical
`async{}` _coroutine builder_:

```kotlin
fun <T> async(block: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    block.startCoroutine(completion = object : Continuation<T> {
        override fun resume(value: T) { 
            future.complete(value) 
        }
        override fun resumeWithException(exception: Throwable) { 
            future.completeExceptionally(exception) 
        }
    })
    return future
}
```

> You can get this code [here](examples/async.kt)  

Normally, a coroutine builder function uses some class like Future, Sequence, AsyncTask or alike 
to return the result of the completed coroutine.
In the case of `async{}`, the 
[`ComplatableFuture`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
is used. Builder _starts_ the coroutine, defines coroutine _completion continuation_, and returns. 
The returned object is the public API for the user of this builder. 

The `startCoroutine` is defined in the standard library as an extension for suspending function type. 
Its simplified signature is:

```kotlin
fun <T> (suspend  () -> T).startCoroutine(completion: Continuation<T>)
```

The `startCoroutine` creates coroutine and starts its execution in the current thread 
until the first _suspension point_, then it returns.
Suspension point is an invocation of some [suspending function](#suspending-functions) in the body of the coroutine and
it is up to the code of the corresponding suspending function to define when and how the coroutine execution resumes.

The completion of coroutine invokes _completion continuation_. Its `resume` or `resumeWithException`
functions are invoked when coroutine _completes_ with the result or exception correspondingly.

### Dispatcher

Let's recap [asynchronous UI](#asynchronous-ui) use case. Asynchronous UI applications must ensure that the 
coroutine body itself is always executed in UI thread, despite the fact that various suspending functions 
resume coroutine execution in arbitrary threads. This is accomplished using _continuation dispatcher_. 
First of all, we need to fully understand the lifecycle of a coroutine. Consider a snippet of code that uses 
[`async{}`](#coroutine-builders) coroutine builder:

```kotlin
async {
    initialCode() // execution of initial code
    await(...) // suspension point #1
    block1() // execution #1
    await(...) // suspension point #2
    block2() // execution #2
}
```

Coroutine starts with execution of its `initialCode` until the first suspension point. At the suspension point it
_suspends_ and, after some time, as defined by the corresponding suspending function, it _resumes_ to execute 
`block1`, then it suspends again and resumes to execute `block2`, after which it _completes_.

Continuation dispatcher has an option to wrap the execution of `initialCode`, `block1`, and `block2` from their
resumption to the subsequent suspension points. The initial code of the coroutine is treated as a 
resumption of its _initial continuation_. The standard library provides the following interface:
 
 ```kotlin
interface ContinuationDispatcher {
     fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean = false
     fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean = false
 }

 ```
 
 The `dispatchResume` function wraps the execution of the coroutine from its resumption with some value to 
 the subsequent suspension,
 while `dispatchResumeWithException` does the same for resumptions with exception. Both are defined to return
 `Boolean` — `false` result means that dispatcher did not do anything and the execution must proceed normally in 
 the current thread, `true` result means that the execution was dispatched into some other thread and it becomes
 responsibility of the dispatcher to invoke `continuation.resume` or `continuation.resumeWithException`
 correspondingly.
 
 Here is an example code for `SwingDispatcher`:
  
 ```kotlin
 object SwingDispatcher : ContinuationDispatcher {
     override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
         if (SwingUtilities.isEventDispatchThread()) return false
         SwingUtilities.invokeLater { continuation.resume(value) }
         return true
     }
 
     override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
         if (SwingUtilities.isEventDispatchThread()) return false
         SwingUtilities.invokeLater { continuation.resumeWithException(exception) }
         return true
     }
 }
 ``` 
 
 If the execution already happens in UI thread, then `SwingDispatcher` just returns `false`, otherwise it dispatches
 execution of the continuation onto Swing UI thread until its next suspension. Using this dispatcher, it is
 straightforward to implement `asyncSwing` coroutine builder that was 
 shown in [asynchronous UI](#asynchronous-ui) use case:
 
 ```kotlin
 fun <T> asyncSwing(block: suspend () -> T): CompletableFuture<T> {
     val future = CompletableFuture<T>()
     block.startCoroutine(completion = object : Continuation<T> {
         override fun resume(value: T) { 
             future.complete(value) 
         }
         override fun resumeWithException(exception: Throwable) { 
             future.completeExceptionally(exception) 
         }
     }, dispatcher = SwingDispatcher) // Note the dispatcher parameter to startCoroutine
     return future
 }

 ```
  
> You can get this code [here](examples/swing.kt)  
  
Its only difference from `async` builder is the additional argument to `startCoroutine`, which is actually defined
in the standard library in the following way:

```kotlin
fun <T> (suspend () -> T).startCoroutine(
    completion: Continuation<T>, 
    dispatcher: ContinuationDispatcher? = null)
```

### Restricted suspension

A different kind of coroutine builder and suspension function is needed to implement `generate{}` and `yield()`
from [generators](#generators) use case. Here is the library code for `generate{}` coroutine builder: 

```kotlin
fun <T> generate(block: suspend Generator<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorIterator<T>()
        iterator.nextStep = block.createCoroutine(receiver = iterator, completion = iterator)
        return iterator
    }
}
```

It uses a different primitive from the standard library called `createCoroutine` that _creates_ coroutine, 
but does _not_ start it. Instead, it returns its _initial continuation_ as a reference to `Continuation<Unit>`. 
The other difference is that _suspending lambda_
`block` for this builder is an 
[_extension lambda_](https://kotlinlang.org/docs/reference/lambdas.html#function-literals-with-receiver) 
with `Generator<T>` receiver.
The `Generator` interface provides the _scope_ for the generator block and is defined in a library as:

```kotlin
interface Generator<in T> {
    suspend fun yield(value: T)
}
```

To avoid creation of multiple objects, `generate{}` implementation defines `GeneratorIterator<T>` class that
implements `Generator<T>` and also implements `Continuation<Unit>`, so it can serve both as 
a `receiver` parameter for `createCoroutine` and as its `completion` continuation parameter. 
The simple implementation for `GeneratorIterator<T>` is shown below:

```kotlin
private class GeneratorIterator<T>: AbstractIterator<T>(), Generator<T>, Continuation<Unit> {
    lateinit var nextStep: Continuation<Unit>

    // AbstractIterator implementation
    override fun computeNext() { nextStep.resume(Unit) }

    // Completion continuation implementation
    override fun resume(value: Unit) { done() }
    override fun resumeWithException(exception: Throwable) { throw exception }

    // Generator implementation
    override suspend fun yield(value: T) {
        setNext(value)
        return suspendCoroutine { c -> nextStep = c }
    }
}
```
 
 > You can get this code [here](examples/generate.kt)  
 
The implementation of `yield` uses `suspendCoroutine` [suspending function](#suspending-functions) to suspend
the coroutine and to capture its continuation. Continuation is stored as `nextStep` to be resumed when the 
`computeNext` is invoked.
 
However, `generate{}` and `yield()`, as shown above, are not ready for an arbitrary suspending function
to capture the continuation in their scope. They work _synchronously_.
They need absolute control on how continuation is captured, 
where it is stored, and when it is resumed. They form _restricted suspension scope_. 
The ability to restrict suspensions is provided by `@RestrictsSuspension` annotation that is placed
on the scope class or interface, in the above example this scope interface is `Generator`:

```kotlin
@RestrictsSuspension
interface Generator<in T> {
    suspend fun yield(value: T)
}
```

This annotation enforces certain restrictions on suspending functions that can be used in the
scope of `generate{}` or similar synchronous coroutine builder. 
Any extension suspending lambda or function that has _restricted suspension scope_ class or interface 
(marked with `@RestrictsSuspension`) as its receiver is 
called a _restricted suspending function_.
Restricted suspending functions can only invoke member or
extension suspending functions on the same instance of their restricted suspension scope. 
In particular, it means that
no `Generator` extension of lambda in its scope can invoke `suspendContinuation` or other 
general suspending function. To suspend the execution of a `generate` coroutine they must ultimately invoke
 `Generator.yield`. The implementation of `yield` itself is a member function of `Generator`
implementation and it does not have any restrictions (only _extension_ suspending lambdas and functions are restricted).

## More examples

This is a non-normative section that does not introduce any new language constructs or 
library functions, but shows how all the building blocks compose to cover a large variety
of use-cases.

### Wrapping callbacks

Many asynchronous APIs have callback-style interfaces. The `suspendCoroutine` suspending function 
from the standard library provides for
an easy way to wrap any callback into a Kotlin suspending function. For example,
`File.aRead()` function from [asynchronous computations](#asynchronous-computations) use case 
can be implemented as a suspending function on top of Java NIO 
[`AsynchronousFileChannel`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/AsynchronousFileChannel.html)
and its 
[`CompletionHandler`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/CompletionHandler.html)
callback interface with the following code:

```kotlin
suspend fun File.aRead(): ByteArray {
    val channel = AsynchronousFileChannel.open(toPath());
    val tentativeSize = channel.size()
    if (tentativeSize > Int.MAX_VALUE) throw IOException("File is too large to read into byte array")
    val buffer = ByteBuffer.allocate(tentativeSize.toInt())
    return suspendCoroutine { c ->
        channel.read(buffer, 0L, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(bytesRead: Int, attachment: Unit) {
                val n = bytesRead.coerceAtLeast(0)
                val bytes = if (n == buffer.capacity()) buffer.array() 
                    else buffer.array().copyOf(n)
                c.resume(bytes)
            }

            override fun failed(exception: Throwable, attachment: Unit) {
                c.resumeWithException(exception)
            }
        })
    }
}
```

> You can get this code [here](examples/io.kt)  

### Cooperative single-thread multitasking

It is very convenient to write cooperative single-threaded applications, because you don't have to 
deal with concurrency and shared mutable state. JS, Python and many other languages do 
not have threads, but have cooperative multitasking primitives.

Let's implement `asyncThread{}` coroutine builder that is similar to a 
[`thread{}`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.concurrent/thread.html)
from the standard library. However, instead of a regular functional block to execute in
the new thread, it takes a suspending lambda block. It allows to run multiple
asynchronous tasks, sleep, wait, etc, all cooperatively from a single thread,
e.g. the goal is to automagically make the following code work in a single thread, despite the 
fact that it has two asynchronous tasks inside that are both active.


```kotlin
asyncThread("MyEventThread") {
    log("Hello, world!")
    val f1 = async {
        log("f1 is sleeping")
        sleep(1000) // sleep 1s
        log("f1 returns 1")
        1
    }
    val f2 = async {
        log("f2 is sleeping")
        sleep(1000) // sleep 1s
        log("f2 returns 2")
        2
    }
    log("I'll wait for both f1 and f2. It should take just a second!")
    val sum = await(f1) + await(f2)
    log("And the sum is $sum")
}
```

Let us start by defining a scope class for cooperative multitasking. The minimal version might
look like this:

```kotlin
interface AsyncThreadScope {
    fun <T> async(block: suspend AsyncThreadScope.() -> T): CompletableFuture<T>
    suspend fun sleep(time: Long)
}
```

This scope interface defines `async` and `sleep` to start sub-tasks in the same thread 
and to efficiently sleep in a cooperative way. 
The `@RestrictsSuspension` annotation on the scope interface is _not_ needed.
We'll have _composability_ with arbitrary 3rd party suspending functions, 
like asynchronous IO as shown in [wrapping callbacks](#wrapping-callbacks) section,
 with `await` suspending function as shown in [suspending functions](#suspending-functions) section, etc.
 
In order to dispatch all suspensions inside the `asyncThread{}` coroutine into _the_ thread, 
dispatcher is installed in a coroutine as explained in the [dispatcher](#dispatcher) section:


```kotlin
fun <T> asyncThread(name: String, block: suspend AsyncThreadScope.() -> T): CompletableFuture<T> {
    val scope = AsyncThreadScopeImpl<T>(name)
    block.startCoroutine(receiver = scope, completion = scope, dispatcher = scope)
    scope.thread.start()
    return scope.completionFuture
}
```

> You can get full code [here](examples/async-thread.kt)  

Implementation of the `AsyncThreadScopeImpl` itself becomes just a technicality.
  
## Asynchronous sequences

The `generate{}` coroutine builder that is shown in [restricted suspension](#restricted-suspension)
section is an example of a _synchronous_ coroutine. Its producer code in the coroutine is invoked
synchronously in the same thread as soon as its consumer invokes `Iterator.next()`. 
The `generate{}` coroutine block is restricted and it cannot suspend its execution using 3rd-party suspending 
functions like asynchronous file IO as shown in [wrapping callbacks](#wrapping-callbacks) section.

_Asynchronous_ generator is allowed to arbitrarily suspend and resume its execution. It means
that its consumer shall be ready to handle the case, when the data is not produced yet. This is
a natural use-case for suspending functions. Let us define `AsyncIterator` interface that is 
similar to a regular 
[`Iterator`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-iterator/) 
interface, but its `next()` and `hasNext()` functions are suspending:
 
```kotlin
interface AsyncIterator<out T> {
    suspend operator fun hasNext(): Boolean
    suspend operator fun next(): T
}
```

The definition of `AsyncSequence` is similiar to the standard synchronous
[`Sequence`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.sequences/-sequence/index.html):

```kotlin
interface AsyncSequence<out T> {
    operator fun iterator(): AsyncIterator<T>
}
```

We also define a scope interface for asynchronous generators that is similar 
to a scope of a synchronous generator, but it is not restricted in its suspensions:

```kotlin
interface AsyncGenerator<in T> {
    suspend fun yield(value: T)
}
```

The builder function `asyncGenerate{}` has a signature and implementation that
is similar to a synchronous `generate{}`. Their differences lie in implementation
details of `AsyncGeneratorIterator`:

```kotlin
fun <T> asyncGenerate(block: suspend AsyncGenerator<T>.() -> Unit): AsyncSequence<T> = object : AsyncSequence<T> {
    override fun asyncIterator(): AsyncIterator<T> {
        val iterator = AsyncGeneratorIterator<T>()
        iterator.nextStep = block.createCoroutine(receiver = iterator, completion = iterator)
        return iterator
    }
}
```

> You can get full code [here](examples/asyncGenerate.kt)

Let us take `asyncThread{}` builder from 
[cooperative single-thread multitasking](#cooperative-single-thread-multitasking) section 
to illustrate the use of asynchronous sequences. It provides us with a suspending function 
`sleep` that suspends the execution for a given time without blocking, so that we can 
write an implementation of a non-blocking asynchronous sequence that yields integers from 1 to 10,
sleeping 500 ms between them:
 
```kotlin
val seq = asyncGenerate {
    for (i in 1..10) {
        yield(i)
        sleep(500L)
    }
}
```
   
Now the consumer coroutine can consume this sequence at its own pace, while also 
suspending with other arbitrary suspending functions. Note, that 
Kotlin [for loops](https://kotlinlang.org/docs/reference/control-flow.html#for-loops)
work by convention, so there is no need for a special `await for` loop construct in the language.
The regular `for` loop can be used to iterate over an asynchronous sequence that we've defined
above. It is suspended whenever producer does not have a value:


```kotlin
for (value in seq) { // suspend while waiting for producer
    // do something with value here, may suspend here, too
}
```

> You can find a worked out example with some logging that illustrates the execution
  [here](examples/asyncGenerate-test.kt)
  
  
### Channels

Go-style type-safe channels can be implemented in Kotlin as a library. We can define an interface for 
send channel with suspending function `send`:

```kotlin
interface SendChannel<T> {
    suspend fun send(value: T)
    fun close()
}
```
  
and receiver channel with suspending function `receive` and an `operator iterator` in a similar style 
to [asynchronous sequences](#asynchronous-sequences):

```kotlin
interface ReceiveChannel<T> {
    suspend fun receive(): T
    suspend operator fun iterator(): ReceiveIterator<T>
}
```

The `Channel<T>` class implements both interfaces.
The `send` suspends when the channel buffer is full, while `receive` suspends when the buffer is empty.
It allows us to copy Go-style code into Kotlin almost verbatim.
The `fibonacci` function that sends `n` fibonacci numbers in to a channel from
[the 4th concurrency example of a tour of Go](https://tour.golang.org/concurrency/4)  would look 
like this in Kotlin:

```kotlin
suspend fun fibonacci(n: Int, c: SendChannel<Int>) = suspending {
    var x = 0
    var y = 1
    for (i in 0..n - 1) {
        c.send(x)
        val next = x + y
        x = y
        y = next
    }
    c.close()
}
```

> Note, that we've also defined `suspending` builder [here](examples/suspending.kt) to workaround 
for the current limitation of tail-only calls inside suspending functions. 

We can also define Go-style `go {...}` block to start the new coroutine in some kind of 
multi-threaded pool that dispatches an arbitrary number of light-weight coroutines onto a fixed number of 
actual heavy-weight threads.
The example implementation [here](examples/channel/go.kt) is trivially written on top of
Java's [`ScheduledThreadPoolExecutor`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html)
with the fixed number of threads. Other implementations can be made to optimize thread usage and context switches.

Using this `go` coroutine builder, the main function from the corresponding Go code would look like this:

```kotlin
fun main(args: Array<String>) = go.main {
    val c = Channel<Int>(2)
    go { fibonacci(10, c) }
    for (i in c) { // suspends until value is received from the channel
        println(i)
    }
}
```

> You can checkout working code [here](examples/channel/channel-test-4.kt)

You can freely play with the buffer size of the channel. 
For simplicity, only buffered channels are implemented in the example (with a minimal buffer size of 1), 
because unbuffered channels are conceptually similar to [asynchronous sequences](#asynchronous-sequences)
that were covered before.

Go-style `select` control block that suspends until one of the actions becomes available on 
one of the channels can be implemented as a Kotlin DSL, so that 
[the 5th concurrency example of a tour of Go](https://tour.golang.org/concurrency/5)  would look 
like this in Kotlin:
 
```kotlin
suspend fun fibonacci(c: SendChannel<Int>, quit: ReceiveChannel<Int>) = suspending {
    var x = 0
    var y = 1
    whileSelect {
        c.onSend(x) {
            val next = x + y
            x = y
            y = next
            true // continue while loop
        }
        quit.onReceive {
            println("quit")
            false // break while loop
        }
    }
}
```

> You can checkout working code [here](examples/channel/channel-test-5.kt)
  
Example has an implementation of both `select {...}`, that returns the result of one of its cases like a Kotlin 
[`when` expression](https://kotlinlang.org/docs/reference/control-flow.html#when-expression), 
and a convenience `whileSelect { ... }` that is the same as `while(select<Boolean> { ... })` with fewer braces.
  
The default selection case from [the 6th concurrency example of a tour of Go](https://tour.golang.org/concurrency/6) 
just adds one more case into the `select {...}` DSL:

```kotlin
fun main(args: Array<String>) = go.main {
    val tick = Time.tick(100)
    val boom = Time.after(500)
    whileSelect {
        tick.onReceive {
            println("tick.")
            true // continue loop
        }
        boom.onReceive {
            println("BOOM!")
            false // break loop
        }
        onDefault {
            println("    .")
            sleep(50)
            true // continue loop
        }
    }
}
```

> You can checkout working code [here](examples/channel/channel-test-6.kt)

The `Time.tick` and `Time.after` are trivially implemented 
[here](examples/channel/time.kt), because go-dispatcher provides a suspending 
`sleep` function that suspends a coroutine in a non-blocking fashion using capabilities 
of the underlying thread pool.
  
Other examples can be found [here](examples/channel/) together with the links to 
the corresponding Go code in comments.

Note, that this sample implementation of channels is based on a single
lock to manage its internal wait lists. It makes it easier to understand and reason about. 
However, it never runs user code under this lock and thus it is fully concurrent. 
This lock only somewhat limits its scalability to a very large number of concurrent threads.
This implementation can be optimized to use lock-free disjoint-access-parallel
data structures to scale to a large number of cores without changing its user-facing APIs. 
Channels are not built into the language, 
their implementation code be readily examined, improved, or replaced.

The other important observation is that this channel implementation is independent 
of the underlying dispatcher. It can be used in UI applications 
under an event-thread dispatcher as shown in the 
corresponding [dispatcher](#dispatcher) section, or under any other kind of dispatcher, or without
a dispatcher at all (in the later case, the execution thread is determined solely by the code
of the other suspending functions used in a coroutine).
The channel implementation just provides thread-safe non-blocking suspending functions.
  
You can convince yourself that all the examples [here](examples/channel/) are actually non-blocking and can work
just as well from a single thread, by running them with `-DmaxThreads=1` JVM option
that the sample `go` dispatcher [here](examples/channel/go.kt) is using to configure its thread
pool.

### Mutexes

Writing scalable asynchronous applications is a discipline that one follows, making sure that ones code 
never blocks, but suspends (using suspending functions), without actually blocking a thread.
The Java concurrency primitives like 
[`ReentrantLock`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html)
are thread-blocking and they should not be used in a truly non-blocking code. To control access to shared
resources one can define Go-style `Mutex` that suspends an execution of coroutine instead of blocking it.
The header of the corresponding class would like this:

```kotlin
class Mutex {
    suspend fun lock()
    fun unlock()
}
```

> You can get full implementation [here](examples/mutex.kt)

Using this implementation of non-blocking mutex
[the 9th concurrency example of a tour of Go](https://tour.golang.org/concurrency/9)
can be translated into Kotlin using Kotlin's
[`try-finally`](https://kotlinlang.org/docs/reference/exceptions.html)
that serves the same purpose as Go's `defer`:

```kotlin
class SafeCounter {
    private val v = mutableMapOf<String, Int>()
    private val mux = Mutex()

    suspend fun inc(key: String) = suspending {
        mux.lock()
        try { v[key] = v.getOrDefault(key, 0) + 1 }
        finally { mux.unlock() }
    }

    suspend fun get(key: String): Int? = suspending {
        mux.lock()
        try { v[key] }
        finally { mux.unlock() }
    }
}
```

> You can checkout working code [here](examples/channel/channel-test-9.kt)

## Advanced topics

This section covers some advanced topics dealing with resource management, concurrency, 
and programming style.

### Resource management and GC

Coroutines don't use any off-heap storage and do not consume any native resources by themselves, unless the code
that is running inside a coroutine does open a file or some other resource. While files opened in a coroutine must
be closed somehow, the coroutine itself does not need to be closed. When coroutine is suspended its whole state is 
available by the reference to its continuation. If you loose the reference to suspended coroutine's continuation,
then it will be ultimately collected by garbage collector.

Coroutines that open some closeable resources deserve a special attention. Consider the following coroutine
that uses the `generate{}` builder from [restricted suspension](#restricted-suspension) section to produce
a sequence of lines from a file:

```kotlin
fun sequenceOfLines(fileName: String) = generate<String> {
    BufferedReader(FileReader(fileName)).use {
        while (true) {
            yield(it.readLine() ?: break)
        }
    }
}
```

This function returns a `Sequence<String>` and you can use this function to print all lines from a file 
in a natural way:
 
```kotlin
sequenceOfLines("examples/sequenceOfLines.kt")
    .forEach(::println)
```

> You can get full code [here](examples/sequenceOfLines.kt)

It works as expected as long as you iterate the sequence returned by the `sequenceOfLines` function 
completely. However, if you print just a few first lines from this file like here:

```kotlin
sequenceOfLines("examples/sequenceOfLines.kt")
        .take(3)
        .forEach(::println)
```

then the coroutine resumes a few times to yield the first three lines and becomes _abandoned_.
It is Ok for the coroutine itself to be abandoned but not for the open file. The 
[`use` function](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.io/use.html) 
will not have a chance to finish its execution and close the file. The file will be left open
until collected by GC, because Java files have a `finalizer` that closes the file. It is 
not a big problem for a small slide-ware or a short-running utility, but it may be a disaster for
a large backend system with multi-gigabyte heap, that can run out of open file handles 
faster than it runs out of memory to trigger GC.

This is a similar gotcha to Java's 
[`Files.lines`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html#lines-java.nio.file.Path-)
method that produces a lazy stream of lines. It returns a closeable Java stream, but most stream operations do not
automatically invoke the corresponding 
`Stream.close` method and it is up to the user to remember about the need to close the corresponding stream. 
One can define closeable sequence generators 
in Kotlin, but they will suffer from a similar problem that no automatic mechanism in the language can
ensure that they are closed after use. It is explicitly out of the scope of Kotlin coroutines
to introduce a language mechanism for an automated resource management.

However, usually this problem does not affect asynchronous use-cases of coroutines. An asynchronous coroutine 
is never abandoned, but ultimately runs until its completion, so if the code inside a coroutine properly closes
its resources, then they will be ultimately closed.

### Concurrency and threads

Each individual coroutine, just like a thread, is executed sequentially. It means that the following kind 
of code is perfectly safe inside a coroutine:

```kotlin
async { // starts a coroutine
    val m = mutableMapOf<String, String>()
    val v1 = await(someAsyncTask1()) // suspends on await
    m["k1"] = v1 // modify map when resumed
    val v2 = await(someAsyncTask2()) // suspends on await
    m["k2"] = v2 // modify map when resumed
}
```

You can use all the regular single-threaded mutable structures inside the scope of a particular coroutine.
However, sharing mutable state _between_ coroutines is potentially dangerous. If you use a coroutine builder
that install a dispatcher to resume all coroutines JS-style in the single event-dispatch thread, 
like the `asyncSwing{}`shown in [dispatcher](#dispatcher) section, then you can safely work with all shared
objects that are generally modified from this event-dispatch thread. 
However, if you use mutli-threaded coroutine builder or otherwise share mutable state between 
coroutines running in different threads, then you have to use thread-safe (concurrent) data structures. 

Coroutines are like threads, albeit they are more lightweight. You can have millions of coroutines running on 
just a few threads. The running coroutine is always executed in some thread. However, a _suspended_ coroutine
does not consume a thread and it is not bound to a thread in any way. The suspending fuction that resumes this
coroutine decides which thread the coroutine is resumed on by invoking `Continuation.resume` on this thread 
and coroutine's dispatcher can override this decision and dispatch the coroutine's execution onto a different thread.

## Asynchronous programming styles

There are different styles of asynchronous programming.
 
Callbacks were discussed in [asynchronous computations](#asynchronous-computations) section and are generally
the least convenient style that coroutines are designed to replace. Any callback-style API can be
wrapped into the corresponding suspending function as shown [here](#wrapping-callbacks). 

Let us recap. For example, assume that you start with a hypothetical _blocking_ `sendEmail` function 
with the following signature:

```kotlin
fun sendEmail(emailArgs: EmailArgs): EmailResult
```

It blocks execution thread for potentially long time while it operates.

To make it non-blocking you can use, for example, error-first 
[node.js callback convention](https://www.tutorialspoint.com/nodejs/nodejs_callbacks_concept.htm)
to represent its non-blocking version in callback-style with the following signature:

```kotlin
fun sendEmail(emailArgs: EmailArgs, callback: (Throwable?, EmailResult?) -> Unit)
```

However, coroutines enable other styles of asynchronous non-blocking programming. One of them
is `async`/`await` style that is built into many popular languages. 
In Kotlin this style can be replicated by introducing `async{}` and `await()` library functions
that were shown as a part of [futures](#futures) use-case section.
 
This style is signified by the convention to return some kind of future object from the function instead 
of taking a callback as a parameter. In this async-style the signature of `sendEmail` is going to look like this:

```kotlin
fun sendEmailAsync(emailArgs: EmailArgs): Future<EmailResult>
```

As a matter of style, it is a good practise to add `Async` suffix to such method names, because their 
parameters are no different from a blocking version and it is quite easy to make a mistake of forgetting about
asynchronous nature of their operation. The function `sendEmailAsync` starts a _concurrent_ asynchronous operation 
and potentially brings with it all the pitfalls of concurrency. However, languages that promote this style of 
programming also typically have some kind of `await` primitive to bring the execution back into the sequence as needed. 

Kotlin's _native_ programming style is based on suspending functions. In this style, the signature of 
`sendEmail` looks naturally, without any mangling to its parameters or return type but with an additional
`suspend` modifier:

```kotlin
suspend fun sendEmail(emailArgs: EmailArgs): EmailResult
```

The async and suspending styles can be easily converted into one another using the primitives that we've 
already seen. For example, `sendEmailAsync` can be implemented via suspending `sendEmail` using
[`async` coroutine builder](#coroutine-builders):

```kotlin
fun sendEmailAsync(emailArgs: EmailArgs): Future<EmailResult> = async {
    sendEmail(emailArgs)
}
```

while suspending function `sendEmail` can be implemented via `sendEmailAsync` using
[`await` suspending function](#suspending-functions)

```kotlin
suspend fun sendEmail(emailArgs: EmailArgs): EmailResult = 
    await(sendEmailAsync(emailArgs))
```

So, in some sense, these two styles are equivalent and are both definitely superior to callback style in their
convenience. However, let us look deeper at a difference between `sendEmailAsync` and suspending `sendEmail`.

Let's compare how they **compose** first. Suspending functions can be composted using 
`suspending` builder that is trivially implemented [here](examples/suspending.kt):

```kotlin
suspend fun largerBusinessProcess() = suspending {
    // a lot of code here, then somewhere inside
    sendEmail(emailArgs)
    // something else goes on after that
}
```

> Note: the restriction on suspending function invocation will be lifted in the future and
`suspending{}` coroutine builder in this example will not be required.

The corresponding async-style functions compose in this way:

```kotlin
fun largerBusinessProcessAsync() = async {
   // a lot of code here, then somewhere inside
   await(sendEmailAsync(emailArgs))
   // something else goes on after that
}
```

Observe, that async-style function composition is more verbose and _error prone_. 
If you omit `await(...)` invocation in async-style 
example,  the code still compiles and works, but it now does email sending process 
asynchronously or even _concurrently_ with the rest of a larger business process, 
thus potentially modifying some shared state and introducing some very hard to reproduce errors.
On the contrary, suspending functions are _sequential by default_.
With suspending functions, whenever you need any concurrency, you explicitly express it in the source code with 
some kind of `async{}` or a similar coroutine builder invocation.

Compare how these styles **scale** for a big project using many libraries. Suspending functions are  
a light-weight language concept in Kotlin. All suspending functions are fully usable in any unrestricted Kotlin coroutine.
Async-style functions are framework-dependent. Every promises/futures framework must define its own `async`-like 
function that returns its own kind of promise/future class and its own `await`-like function, too.

Compare their **performance**. Suspending functions provide minimal overhead per invocation. 
You can checkout [implementation details](#implementation-details) section.
Async-style functions need to keep quite heavy promise/future abstraction in addition to all of that suspending machinery. 
Some future-like object instance must be always returned from async-style function invocation and it cannot be optimized away even 
if the function is very short and simple. Async-style is not well-suited for very fine-grained decomposition.

Compare their **interoperability** with JVM/JS code. Async-style functions are more interoperable with JVM/JS code that 
uses a matching type of future-like abstraction. In Java or JS they are just functions that return a corresponding
future-like object. Suspending functions look strange from any language that does not support 
[continuation-passing-style](#continuation-passing-style) natively.
However, you can see in the examples above how easy it is to convert any suspending function into an 
async-style function for any given promise/future framework. So, you can write suspending function in Kotlin just once, 
and then adapt it for interop with any style of promise/future with one line of code using an appropriate 
`async{}` coroutine builder function. 

## Implementation details

This section provides a glimpse into implementation details of coroutines. They are hidden
behind the building blocks explained in [coroutines overview](#coroutines-overview) section and
their internal classes and code generation strategies are subject to change at any time as
long as they don't break contracts of public APIs and ABIs.

### Continuation passing style

Suspending functions are implemented via Continuation-Passing-Style (CPS).
Every suspending function and suspending lambda has an additional `Continuation` 
parameter that is implicitly passed to it when it is invoked. Recall, that a declaration
of [`await` suspending function](#suspending-functions) looks like this:

```kotlin
suspend fun <T> await(f: CompletableFuture<T>): T
```

However, it's actual _implementation_ has the following signature after _CPS transformation_:

```kotlin
fun <T> await(f: CompletableFuture<T>, continuation: Continuation<T>): Any?
```

Its result type `T` had moved into a position of type argument in its additional continuation parameter.
The implementation result type of `Any?` is designed to represent the action of the suspending function.
When suspending function _suspends_ coroutine, it returns a special marker value of 
`CoroutineIntrinsics.SUSPENDED`. When suspending function does not suspend coroutine, but 
continues coroutine execution, it returns its result or throws exception directly.
This way, the `Any?` return type of `await` implementation is actually a union of 
`CoroutineIntrinsics.SUSPENDED` and `T` that cannot be expressed in Kotlin's type-system.

The actual implementation of suspending function is not allowed to invoke continuation in its stack-frame directly,
because that may lead to stack overflow on long-running coroutines. The `suspendCoroutine` function in
the standard library hides this complexity from an application developer by tracking invocations
of continuation and ensures conformance to the actual implementation contract of 
suspending functions regardless of how and when continuation is invoked.

### State machines

It is crucial to implement coroutines efficiently, i.e. create as few classes and objects as possible.
Many languages implement them through _state machines_ and Kotlin does the same. In the case of Kotlin 
this approach results in the compiler creating only one class per suspending lambda that may
have an arbitrary number of suspension points in its body.   
 
Main idea: a suspending function is compiled to a state machine, where states correspond to suspension points. 
Example: let's take a suspending block with two suspension points:
 
```kotlin
val a = a()
val y = await(foo(a)) // suspension point #1
b()
val z = await(bar(a, y)) // suspension point #2
c(z)
``` 
 
For this block of code there are three states:
 
 * initial (before any suspension point)
 * after the first suspension point
 * after the second suspension point
 
Every state is an entry point to one of the continuations of this block 
(the initial continuation continues from the very first line). 
 
The code is compiled to an anonymous class that has a method implementing the state machine, 
a field holding the current state of the state machine, and fields for local variables of 
the coroutine that are shared between states (there may also be fields for the closure of 
the coroutine, but in this case it is empty). Here's pseudo-Java code for the block above
that uses continuation passing style for invocation of suspending functions `await`:
  
``` java
class <anonymous_for_state_machine> extends CoroutineImpl<...> implements Continuation<Object> {
    // The current state of the state machine
    int label = 0
    
    // local variables of the coroutine
    A a = null
    Y y = null
    
    void resume(Object data) {
        if (label == 0) goto L0
        if (label == 1) goto L1
        if (label == 2) goto L2
        else throw IllegalStateException()
        
      L0:
        // data is expected to be `null` at this invocation
        a = a()
        label = 1
        data = await(foo(a), this) // 'this' is passed as a continuation 
        if (data == SUSPENDED) return // return if await had suspended execution
      L1:
        // external code has resumed this coroutine passing the result of await() as data 
        y = (Y) data
        b()
        label = 2
        data = await(bar(a, y), this) // 'this' is passed as a continuation
        if (data == SUSPENDED) return // return if await had suspended execution
      L2:
        // external code has resumed this coroutine passing the result of await() as data 
        Z z = (Z) data
        c(z)
        label = -1 // No more steps are allowed
        return
    }          
}    
```  

Note that there is a `goto` operator and labels, because the example depicts what happens in the 
byte code, not in the source code.

Now, when the coroutine is started, we call its `resume()` — `label` is `0`, 
and we jump to `L0`, then we do some work, set the `label` to the next state — `1`, call `await()` 
and return if the execution of the coroutine was suspended. 
When we want to continue the execution, we call `resume()` again, and now it proceeds right to 
`L1`, does some work, sets the state to `2`, calls `await()` and again returns in case of suspension. 
Next time it continues from `L3` setting the state to `-1` which means 
"over, no more work to do". 

A suspension point inside a loop generates only one state, 
because loops also work through (conditional) `goto`:
 
```kotlin
var x = 0
while (x < 10) {
    x += await(nextNumber())
}
```

is generated as

``` java
class <anonymous_for_state_machine> extends CoroutineImpl<...> implements Continuation<Object> {
    // The current state of the state machine
    int label = 0
    
    // local variables of the coroutine
    int x
    
    void resume(Object data) {
        if (label == 0) goto L0
        if (label == 1) goto L1
        else throw IllegalStateException()
        
      L0:
        x = 0
      LOOP:
        if (x > 10) goto END
        label = 1
        data = await(nextNumber(), this) // 'this' is passed as a continuation 
        if (data == SUSPENDED) return // return if await had suspended execution
      L1:
        // external code has resumed this coroutine passing the result of await() as data 
        x += ((Integer) data).intValue()
        label = -1
        goto LOOP
      END:
        label = -1 // No more steps are allowed
        return 
    }          
}    
```  

### Coroutine intrinsics

The actual implementation of `suspendCoroutine` suspending function in the standard library is written in Kotlin
itself and its source code is a available as a part of the standard library sources package. In order to provide for the
safe and problem-free use of coroutines it wraps the actual continuation of the state machine 
into an additional object on each suspension of coroutine. This is perfectly fine for truly asynchronous use cases
like [asynchronous computations](#asynchronous-computations) and [futures](#futures), since the runtime costs of the 
corresponding asynchronous primitives far outweigh the cost of an additional allocated object. However, for
the [generators](#generators) use case this additional cost is prohibitive.

The `CoroutineIntrinsics` object in the standard library contains the function named `suspendCoroutineOrReturn`
with the following signature:

```kotlin
suspend fun <T> suspendCoroutineOrReturn(block: (Continuation<T>) -> Any?): T
```

It provides direct access to [continuation passing style](#continuation-passing-style) of suspending functions
and direct access to [state machine](#state-machines) implementation of coroutine. The user of 
`suspendCoroutineOrReturn` bears full responsibility of following CPS result convention, but gains slightly
better performance as a result. This convention is usually easy to follow for `generate`/`yield`-like coroutines, but attempts to 
write asynchronous `await`-like suspending functions on top of `suspendCoroutineOrReturn` are **discouraged**
as they are **extremely tricky** to implement correctly without the help of `suspendCoroutine` and errors in these
implementation attempts are typically [heisenbugs](https://en.wikipedia.org/wiki/Heisenbug)
that defy attempts to find and reproduce them via tests. 

Optimized version of `yield` via `CoroutineIntrinsics.suspendCoroutineOrReturn` is shown below.
Because `yield` always suspends to pass the control back to the consumer of the sequence, 
the corresponding block always returns `CoroutineIntrinsics.SUSPENDED`.

```kotlin
// Generator implementation
override suspend fun yield(value: T) {
    setNext(value)
    return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
        nextStep = c
        CoroutineIntrinsics.SUSPENDED
    }
}
```

> You can get full code [here](examples/generateOptimized.kt)  
 

