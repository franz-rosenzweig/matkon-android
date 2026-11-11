-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
# Gson reads Matkon's JSON into these classes by field name.
-keep class xyz.tiresia.matkon.data.** { *; }
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class xyz.tiresia.matkon.timer.RunningTimer { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
