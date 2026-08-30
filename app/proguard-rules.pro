# Queste regole valgono solo se un giorno R8 venisse riacceso.
#
# Al momento la release NON e' minificata: vedi il commento in
# app/build.gradle.kts. La 0.4.2 e' stata pubblicata con R8 attivo e il
# riconoscimento non partiva affatto.

# MediaPipe Tasks usa JNI e reflection: va tenuto integro.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# Flogger, la libreria di log che MediaPipe usa internamente, ricava il nome
# della classe chiamante camminando sullo stack. Se R8 rinomina, unisce classi
# o incorpora metodi, il fotogramma che cerca non c'e' piu' e lancia
# `IllegalStateException: no caller found on the stack`. Succede dentro
# l'inizializzatore statico di `com.google.mediapipe.framework.Graph`, quindi
# non parte niente.
#
# Tenere i nomi aiuta ma non basta da solo: `-keep` impedisce di rinominare una
# classe, non di cambiare la forma dello stack attorno a lei. Per questo la
# release e' non minificata.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod

# I servizi dichiarati nel manifest sono istanziati dal sistema.
-keep class dev.airscroll.app.service.** { *; }
