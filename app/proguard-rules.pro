# MediaPipe Tasks usa JNI e reflection: va tenuto integro.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# I servizi dichiarati nel manifest sono istanziati dal sistema.
-keep class dev.airscroll.app.service.** { *; }
