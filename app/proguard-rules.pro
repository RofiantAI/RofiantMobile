# androidx.security-crypto pulls in Google Tink, which references
# errorprone's compile-only annotations that aren't on the runtime
# classpath — safe to ignore per Tink's own R8 guidance.
-dontwarn com.google.errorprone.annotations.**
