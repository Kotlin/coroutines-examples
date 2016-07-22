package try_finally

fun <T> test(iterable: Iterable<T>) {
    for (item in iterable) {
        consume(item)
    }
}
/*

  // access flags 0x19
  // signature <T:Ljava/lang/Object;>(Ljava/lang/Iterable<+TT;>;)V
  // declaration: void test<T>(java.lang.Iterable<? extends T>)
  public final static test(Ljava/lang/Iterable;)V
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
   L0
    ALOAD 0
    LDC "iterable"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 4 L1
    ALOAD 0
    INVOKEINTERFACE java/lang/Iterable.iterator ()Ljava/util/Iterator;
    ASTORE 2
   L2
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.hasNext ()Z
    IFEQ L3
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.next ()Ljava/lang/Object;
    ASTORE 1
   L4
    LINENUMBER 5 L4
    ALOAD 1
    INVOKESTATIC try_finally/Try_finally_experimentsKt.consume (Ljava/lang/Object;)V
   L5
    LINENUMBER 4 L5
   L6
    GOTO L2
   L3
    LINENUMBER 7 L3
    RETURN
   L7
    LOCALVARIABLE item Ljava/lang/Object; L4 L6 1
    LOCALVARIABLE iterable Ljava/lang/Iterable; L0 L7 0
    MAXSTACK = 2
    MAXLOCALS = 3

 */

fun <T> testWithFinally(iterable: Iterable<T>) {
    try {
        for (item in iterable) {
            consume(item)
        }
    } finally {
        disposeIfNeeded(null)
    }
}
/*
  // access flags 0x19
  // signature <T:Ljava/lang/Object;>(Ljava/lang/Iterable<+TT;>;)V
  // declaration: void testWithFinally<T>(java.lang.Iterable<? extends T>)
  public final static testWithFinally(Ljava/lang/Iterable;)V
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
    TRYCATCHBLOCK L0 L1 L2 null
    TRYCATCHBLOCK L2 L3 L2 null
   L4
    ALOAD 0
    LDC "iterable"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L5
    LINENUMBER 10 L5
   L6
   L0
    NOP
   L7
    LINENUMBER 11 L7
    ALOAD 0
    INVOKEINTERFACE java/lang/Iterable.iterator ()Ljava/util/Iterator;
    ASTORE 2
   L8
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.hasNext ()Z
    IFEQ L1
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.next ()Ljava/lang/Object;
    ASTORE 1
   L9
    LINENUMBER 12 L9
    ALOAD 1
    INVOKESTATIC try_finally/Try_finally_experimentsKt.consume (Ljava/lang/Object;)V
   L10
    LINENUMBER 11 L10
   L11
    GOTO L8
   L1
    LINENUMBER 15 L1
    ACONST_NULL
    CHECKCAST java/util/Iterator
    INVOKESTATIC try_finally/Try_finally_experimentsKt.disposeIfNeeded (Ljava/util/Iterator;)V
   L12
    GOTO L13
   L2
    ASTORE 1
   L3
    ACONST_NULL
    CHECKCAST java/util/Iterator
    INVOKESTATIC try_finally/Try_finally_experimentsKt.disposeIfNeeded (Ljava/util/Iterator;)V
   L14
    ALOAD 1
    ATHROW
   L15
    LINENUMBER 16 L15
   L13
    LINENUMBER 17 L13
    RETURN
   L16
    LOCALVARIABLE item Ljava/lang/Object; L9 L11 1
    LOCALVARIABLE iterable Ljava/lang/Iterable; L4 L16 0
    MAXSTACK = 2
    MAXLOCALS = 3

 */


fun <T> consume(t: T) {}
fun disposeIfNeeded(iterator: Iterator<*>?) {}