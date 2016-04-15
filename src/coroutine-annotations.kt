package coroutines.annotations

/*
 * The annotations in this file are used as placeholders for the modifiers that are proposed for
 * coroutine-related features
 */

@Target(AnnotationTarget.FUNCTION)
annotation class suspend

@Target(AnnotationTarget.FUNCTION)
annotation class operator

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class coroutine