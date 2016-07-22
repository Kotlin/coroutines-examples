package try_finally;

import java.util.Iterator;

public class JavaForTryFinally {
    <T> void testFinally(Iterable<T> iterable) {
        try {
            for (T t : iterable) {
                consume(t);
            }
        } finally {
             disposeIfNeeded(null);
        }
    }

    private <T> void consume(T t) {}

    static void disposeIfNeeded(Iterator e) {}
}


/*
// class version 52.0 (52)
// access flags 0x21
public class try_finally/JavaForTryFinally {

  // compiled from: JavaForTryFinally.java

  // access flags 0x1
  public <init>()V
   L0
    LINENUMBER 5 L0
    ALOAD 0
    INVOKESPECIAL java/lang/Object.<init> ()V
    RETURN
   L1
    LOCALVARIABLE this Ltry_finally/JavaForTryFinally; L0 L1 0
    MAXSTACK = 1
    MAXLOCALS = 1

  // access flags 0x0
  // signature <T:Ljava/lang/Object;>(Ljava/lang/Iterable<TT;>;)V
  // declaration: void testFinally<T>(java.lang.Iterable<T>)
  testFinally(Ljava/lang/Iterable;)V
    TRYCATCHBLOCK L0 L1 L2 null
    TRYCATCHBLOCK L2 L3 L2 null
   L0
    LINENUMBER 8 L0
    ALOAD 1
    INVOKEINTERFACE java/lang/Iterable.iterator ()Ljava/util/Iterator;
    ASTORE 2
   L4
   FRAME APPEND [java/util/Iterator]
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.hasNext ()Z
    IFEQ L1
    ALOAD 2
    INVOKEINTERFACE java/util/Iterator.next ()Ljava/lang/Object;
    ASTORE 3
   L5
    LINENUMBER 9 L5
    ALOAD 0
    ALOAD 3
    INVOKESPECIAL try_finally/JavaForTryFinally.consume (Ljava/lang/Object;)V
   L6
    LINENUMBER 10 L6
    GOTO L4
   L1
    LINENUMBER 12 L1
   FRAME CHOP 1
    ACONST_NULL
    INVOKESTATIC try_finally/JavaForTryFinally.disposeIfNeeded (Ljava/util/Iterator;)V
   L7
    LINENUMBER 13 L7
    GOTO L8
   L2
    LINENUMBER 12 L2
   FRAME SAME1 java/lang/Throwable
    ASTORE 4
   L3
    ACONST_NULL
    INVOKESTATIC try_finally/JavaForTryFinally.disposeIfNeeded (Ljava/util/Iterator;)V
    ALOAD 4
    ATHROW
   L8
    LINENUMBER 14 L8
   FRAME SAME
    RETURN
   L9
    LOCALVARIABLE t Ljava/lang/Object; L5 L6 3
    // signature TT;
    // declaration: T
    LOCALVARIABLE this Ltry_finally/JavaForTryFinally; L0 L9 0
    LOCALVARIABLE iterable Ljava/lang/Iterable; L0 L9 1
    // signature Ljava/lang/Iterable<TT;>;
    // declaration: java.lang.Iterable<T>
    MAXSTACK = 2
    MAXLOCALS = 5

  // access flags 0x2
  // signature <T:Ljava/lang/Object;>(TT;)V
  // declaration: void consume<T>(T)
  private consume(Ljava/lang/Object;)V
   L0
    LINENUMBER 16 L0
    RETURN
   L1
    LOCALVARIABLE this Ltry_finally/JavaForTryFinally; L0 L1 0
    LOCALVARIABLE t Ljava/lang/Object; L0 L1 1
    // signature TT;
    // declaration: T
    MAXSTACK = 0
    MAXLOCALS = 2

  // access flags 0x8
  static disposeIfNeeded(Ljava/util/Iterator;)V
   L0
    LINENUMBER 18 L0
    RETURN
   L1
    LOCALVARIABLE e Ljava/util/Iterator; L0 L1 0
    MAXSTACK = 0
    MAXLOCALS = 1
}
 */