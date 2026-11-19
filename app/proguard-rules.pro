# NewPipeExtractor runs YouTube's player JavaScript through Rhino, which loads classes by
# reflection, and loads its "time ago" patterns by name. These rules follow NewPipe's own.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**

# NewPipe builds YouTube's request parameters (playlists, channel tabs) with protobuf-lite, which
# finds message fields by reflection.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
