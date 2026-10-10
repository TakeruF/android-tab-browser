# Room and AndroidX ship consumer rules. No JavaScript bridge is exposed.

# Keep the JNI entry points used by AdblockEngine.
-keepclasseswithmembers class org.mlm.adblock.AdblockEngine {
    native <methods>;
}
