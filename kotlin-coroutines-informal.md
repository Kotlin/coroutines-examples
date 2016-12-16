# Coroutines for Kotlin (Revision 2)

* **Type**: Informal description
* **Author**: Andrey Breslav
* **Contributors**: Vladimir Reshetnikov, Stanislav Erokhin, Ilya Ryzhenkov, Denis Zharkov, Roman Elizarov
* **Status**: Implemented in 1.1-M04

## Abstract

This is a description of coroutines in Kotlin. This concept is also known as, or partly covers

- generators/yield
- async/await
- сontinuations

Goals:

- No dependency on a particular implementation of Futures or other such rich library;
- Cover equally the "async/await" use case and "generator blocks".
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
  * [Wrapping callbacks](#wrapping-callbacks)
  * [Dispatcher](#dispatcher)
  * [Restricted suspension](#restricted-suspension)
* [Implementation details](#implementation-details)
  * [Continuation passing style](#continuation-passing-style)
  * [State machines](#state-machines)
  * [Coroutine intrinsics](#coroutine-intrinsics)

## Use cases

A coroutine can be thought of as an instance of _suspendable computation_, i.e. the one that can suspend at some 
points and later continue (possibly on another thread). Coroutines calling each other 
(and passing data back and forth) can form 
the machinery for cooperative multitasking, but this is not exactly the driving use case for us.
 
### Asynchronous computations 
 
The first class of motivating use cases for coroutines are asynchronous computations 
(handled by async/await in C# and other languages). 
Let's take a look at how such computations are done with callbacks. As an inspiration, let's take 
asynchronous I/O (the APIs below are simplified):

``` kotlin
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
need to pass the `buf` parameter explicitly to callbacks, they just see it as part of their closure), the indentation
levels are growing every time, and one can easily anticipate the problems that may come at nesting levels greater 
than one (google for "callback hell" to see how much people suffer from this in JavaScript).

This same computation can be expressed straightforwardly as a coroutine (provided that there's a library that adapts
the I/O APIs to coroutine requirements):
 
``` kotlin
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

``` kotlin
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

``` kotlin
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

``` kotlin
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

``` kotlin
val fibonacci: Sequence<Int> = generate {
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
 
``` kotlin
println(fibonacci.take(10).joinToString())
```

> This will print `1, 1, 2, 3, 5, 8, 13, 21, 34, 55`
  You can try this code [here](examples/fibonacci.kt)  
 
The strength of generators is in supporting arbitrary control flow, such as `while` (from the example above),
`if`, `try`/`catch`/`finally` and everything else: 
 
``` kotlin
val seq = generate {
    yield(firstItem) // suspension point

    for (item in input) {
        if (!it.isValid()) break // don't generate any more items
        val foo = it.toFoo()
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
    
    if (exception != null) {
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
 
All exception handing is performed using natural language constructs. 

### More use cases
 
Coroutines can cover many more use cases, including these:  
 
* Channel-based concurrency (aka goroutines and channels);
* Actor-based concurrency;
* Background processes occasionally requiring user interaction, e.g., show a modal dialog;
* Communication protocols: implement each actor as a sequence rather than a state machine;
* Web application workflows: register a user, validate email, log them in 
(a suspended coroutine may be serialized and stored in a DB).

## Coroutines overview

This section gives a bird's-eye view of the proposed language mechanisms that enable writing coroutines and 
libraries that govern their semantics.  

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
  how its execution and result are represented. For example, `generate` _keyword_ may define a coroutine that 
  returns a certain kind of iterable object, while `async` _keyword_ may define a coroutine that returns a
  certain kind of promise or task. Kotlin does not have keywords or modifiers to define and start a coroutine. 
  Coroutine builders are simply functions defined in a library. 
  In case where a coroutine definition takes the form of a method body in another language, 
  in Kotlin such method would typically be a regular method with an expression body, 
  consisting of an invocation of some library-defined coroutine builder whose last argument is a suspending lambda:
 
 ``` kotlin
 fun asyncTask() = async { ... }
 ```

* A _suspension point_ — is a point during coroutine execution where the execution of the coroutine _may be suspended_. 
Syntactically, a suspension point is an invocation of suspending function, but the actual
suspension happens when the suspending function invokes the standard library primitive to suspend the execution.

* A _continuation_ — is a state of the suspended coroutine at suspension point. It conceptually represents 
the rest of its execution after suspension point. For example:

``` kotlin
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

As mentioned above, one of the driving requirements for this proposal is flexibility:
we want to be able to support many existing asynchronous APIs and other use cases and minimize 
the parts hard-coded into the compiler. As a result, the compiler is only responsible for support
of suspending functions, suspending lambdas, and the corresponding suspending function types. 
There are few primitives in the standard library and the rest is left to application libraries. 

### Continuation interface

Here's is the definition of the standard library interface `Continuation`, which represents
a generic callback:

``` kotlin
interface Continuation<T> {
   fun resume(value: T)
   fun resumeWithException(exception: Throwable)
}
```

### Suspending functions

An implementation of a typical _suspending function_ like `await` looks like this:
  
``` kotlin
suspend fun <T> await(f: CompletableFuture<T>): T =
    suspendCoroutine<T> { c: Continuation<T> ->
        f.whenComplete { result, exception ->
            if (exception == null)
                // the future has been completed normally, resume execution with result
                c.resume(result) 
            else          
                // the future has completed with an exception, resume execution with exception
                c.resumeWithException(exception)
        }
    }
``` 

> You can get this code [here](examples/await.kt)  

The `suspend` modifier indicates that this is a function that can suspend execution of a coroutine. 
Suspend functions may invoke any regular functions, but to actually suspend execution they must
invoke some other suspending function. In particular, this `await` implementation invokes a suspending function 
`suspendCoroutine` that is defined in the standard library in the following way:

``` kotlin
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

Suspending functions cannot be invoked from regular functions, so standard library provides functions
to start coroutine execution from a regular non-suspending scope. Here is the implementation of a typical
`async{}` _coroutine builder_:

``` kotlin
fun <T> async(block: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    block.startCoroutine(completion=object : Continuation<T> {
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
to return the result of the completed coroutine
In the case of `async{}`, the 
[`ComplatableFuture`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
is used. Builder _starts_ the coroutine, defines coroutine _completion continuation_, and returns. 
The returned object is the public API for the user of this builder. 

The `startCoroutine` is defined in a standard library as an extension for suspending function type. 
Its simplified signature is:

``` kotlin
fun <T> (suspend  () -> T).startCoroutine(completion: Continuation<T>)
```

The `startCoroutine` creates coroutine and starts its execution in the current thread 
until the first _suspension point_, then it returns.
Suspension point is an invocation of some [suspending function](#suspending-functions) in the body of coroutine and
it is up to the code of the corresponding suspending function to define when and how the coroutine execution resumes.

The completion of coroutine invokes _completion continuation_. Its `resume` or `resumeWithException`
functions are invoked when coroutine _completes_ with the result or exception correspondingly.

### Wrapping callbacks

Many asynchronous APIs have callback-style interfaces. The `suspendCoroutine` suspending function 
from the standard library provides for
an easy way to wrap any callback into a Kotlin suspending function. For example,
`File.aRead()` function from [asynchronous computations](#asynchronous-computations) use case 
can be implemented as suspending function on top of Java NIO 
[`AsynchronousFileChannel`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/AsynchronousFileChannel.html)
and its 
[`CompletionHandler`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/CompletionHandler.html)
callback interface with the following code:


``` kotlin
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

### Dispatcher

Let's recap [asynchronous UI](#asynchronous-ui) use case. Asynchronous UI applications must ensure that the 
coroutine body itself is always executed in UI thread, despite the fact that various suspending functions 
resume coroutine execution in arbitrary threads. This is accomplished using _continuation dispatcher_. 
First of all, we need to fully understand the lifecycle of a coroutine. Consider a snippet of code that uses 
[`async{}`](#coroutine-builders) coroutine builder:

``` kotlin
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
 
 ``` kotlin
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
  
 ``` kotlin
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
 
 ``` kotlin
 fun <T> asyncSwing(block: suspend () -> T): CompletableFuture<T> {
     val future = CompletableFuture<T>()
     block.startCoroutine(completion=object : Continuation<T> {
         override fun resume(value: T) { 
             future.complete(value) 
         }
         override fun resumeWithException(exception: Throwable) { 
             future.completeExceptionally(exception) 
         }
     }, dispatcher=SwingDispatcher) // Note the dispatcher parameter to startCoroutine
     return future
 }

 ```
  
> You can get this code [here](examples/swing.kt)  
  
Its only difference from `async` builder is the additional argument to `startCoroutine`, which is actually defined
in the standard library in the following way:

``` kotlin
fun <T> (suspend () -> T).startCoroutine(
    completion: Continuation<T>, 
    dispatcher: ContinuationDispatcher? = null)
```

### Restricted suspension

A different kind of coroutine builder and suspension function is needed to implement `generate{}` and `yield()`
from [generators](#generators) use case. Here is the library code for `generate{}` coroutine builder: 

``` kotlin
fun <T> generate(block: suspend Generator<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorIterator<T>()
        val initial = block.createCoroutine(receiver=iterator, completion=iterator)
        iterator.setNextStep(initial)
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

``` kotlin
interface Generator<T> {
    suspend fun yield(value: T)
}
```

To avoid creation of multiple objects, `generate{}` implementation defines `GeneratorIterator<T>` class that
implements `Generator<T>` and also implements `Conitnuation<Unit>`, so it can serve both as 
a `receiver` parameter for `createCoroutine` and as its `completion` continuation parameter. 
The implementation for `GeneratorIterator<T>` is shown below:

``` kotlin
class GeneratorIterator<T>: AbstractIterator<T>(), Generator<T>, Continuation<Unit> {
    private lateinit var nextStep: Continuation<Unit>

    fun setNextStep(step: Continuation<Unit>) { this.nextStep = step }

    // AbstractIterator implementation
    override fun computeNext() { nextStep.resume(Unit) }

    // Completion continuation implementation
    override fun resume(data: Unit) { done() }
    override fun resumeWithException(exception: Throwable) { throw exception }

    // Generator implementation
    override suspend fun yield(value: T) {
        setNext(value)
        return suspendCoroutine { c -> setNextStep(c) }
    }
}

```
 
 > You can get this code [here](examples/yield.kt)  
 
The implementation of `yield` uses `suspendCoroutine` [suspending function](#suspending-functions) to suspend
the coroutine and capture its continuation. Continuation is stored as `nextStep` to be resumed when the 
`computeNext` is invoked.
 
Here is a problem. `generate{}` and `yield()` as shown above are not ready for an arbitrary suspending function
to capture the continuation in the generate scope. They need absolute control on how continuation is captured, 
where it is stored, and when it is resumed. They form _restricted suspension scope_. 
The ability to restrict suspensions is provided by `@RestrictsSuspendExtensions` annotation that is placed
on the scope class or interface, in the above example this scope interface is `Generator`:

``` kotlin
@RestrictsSuspendExtensions
interface Generator<T> {
    suspend fun yield(value: T)
}
```

This annotation enforces certain restrictions on suspending functions that can be used in the
scope of `generate`. Any suspending function
that has _restricted suspension scope_ class or interface as its receiver can only invoke member or
extension suspending functions on the same instance of its receiver scope. In particular, it means that
no `Generator` extension of lambda in its scope can invoke `suspendContinuation` or other 
general suspending function. To suspend the execution of coroutine they must ultimately invoke
 `Generator.yield`. The implementation of `yield` itself is a member function of `GeneratorIterator`
and it does not have any restrictions, because only suspending extension lambdas and functions are restricted.

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

``` kotlin
suspend fun <T> await(f: CompletableFuture<T>): T
```

However, it's actual _implementation_ has the following signature after CPS transformation:

``` kotlin
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
Many languages implement them through _state machines_, and in the case of Kotlin this approach results 
in the compiler creating only one class per coroutine.   
 
Main idea: a coroutine is compiled to a state machine, where states correspond to suspension points. 
Example: let's take a coroutine with two suspension points:
 
``` kotlin
val a = a()
val y = await(foo(a)) // suspension point #1
b()
val z = await(bar(a, y)) // suspension point #2
c(z)
``` 
 
For this coroutine there are three states:
 
 * initial (before any suspension point)
 * after the first suspension point
 * after the second suspension point
 
Every state is an entry point to one of the continuations of this coroutine 
(the initial continuation continues from the very first line). 
 
The code is compiled to an anonymous class that has a method implementing the state machine, 
a field holding the current state of the state machine, and fields for local variables of 
the coroutine that are shared between states (there may also be fields for the closure of 
the coroutine, but in this case it is empty). Here's pseudo-Java code for the coroutine above
that uses continuation passing style for invocation of suspending functions `await`:
  
``` java
class <anonymous_for_state_machine> extends CoroutineImpl<...> implements Cotinuation<Object> {
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
 
``` kotlin
var x = 0
while (x < 10) {
    x += await(nextNumber())
}
```

is generated as

``` java
class <anonymous_for_state_machine> extends CoroutineImpl<...> implements Cotinuation<Object> {
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
safe and problem-free use of coroutines it wraps the actual continuation of the state machine's continuation 
in an additional object on each suspension of coroutine. This is perfectly fine for truly asynchronous use cases
like [asynchronous computations](#asynchronous-computations) and [futures](#futures), since the runtime costs of the 
corresponding asynchronous primitives far outweigh the cost of an additional allocated object. However, for
the [generators](#generators) use case this additional cost is prohibitive.

The `CoroutineIntrinsics` object in the standard library contains the function named `suspendCoroutineOrReturn`
with the following signature:

``` kotlin
suspend fun <T> suspendCoroutineOrReturn(block: (Continuation<T>) -> Any?): T
```

It provides direct access to [continuation passing style](#continuation-passing-style) of suspending functions
and direct access to [state machine](#state-machines) implementation of coroutine. The user of 
`suspendCoroutineOrReturn` bears full responsibility of following CPS result convention, but gains slightly
better performance as a result. This convention is usually easy to follow for `generate`/`yield`-like coroutines, but attempts to 
write asynchronous `await`-like suspending functions on top of `suspendCoroutineOrReturn` are **discouraged**
as they are **extremely tricky** to implement correctly without the help of `suspendCoroutine` and errors in these
implementation attempts are typically of [heisenbugs](https://en.wikipedia.org/wiki/Heisenbug)
that defy attempts to find and reproduce them via tests. 

Optimized version of `yield` via `CoroutineIntrinsics.suspendCoroutineOrReturn` is shown below.
Because `yield` always suspends, the corresponding block always returns `CoroutineIntrinsics.SUSPENDED`.

``` kotlin
     // Generator implementation
     override suspend fun yield(value: T) {
         setNext(value)
         return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
             setNextStep(c)
             CoroutineIntrinsics.SUSPENDED
         }
     }
```

> You can get full code [here](examples/yield-optimized.kt)  
 

