# sherpa-onnx's native code reads its Kotlin config classes by field name and calls back into them.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# Concentus is plain Java, but keep it intact; it is small.
-keep class org.concentus.** { *; }
