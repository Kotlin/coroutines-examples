package noController.api

/**
 * Annotation to mark suspending functions
 */
@Target(AnnotationTarget.FUNCTION)
annotation class suspend

/**
 * Annotation to mark handler operators
 */
@Target(AnnotationTarget.FUNCTION)
annotation class operator

/**
 * Annotation to mark coroutine parameters of builders
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class coroutine

/**
 * Interface representing a coroutine, call the `entryPoint()` function to set a controller
 * and retrieve the starting continuation
 */
interface Coroutine<in C> {
    fun entryPoint(controller: C): Continuation<Unit>
}

/**
 * Interface representing a continuation after a suspension point that returns value of type `P`
 */
interface Continuation<in P> {
    /**
     * Resumes the execution of the corresponding coroutine passing `data` as the return value of the last suspension point
     */
    fun resume(data: P)

    /**
     * Resumes the execution of the corresponding coroutine so that the `exception` is re-thrown right after the
     * last suspension point
     */
    fun resumeWithException(exception: Throwable)
}


