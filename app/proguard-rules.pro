# ProGuard configuration
-keepattributes *Annotation*
-keepclassmembers class * {
    public <init>(...);
}
