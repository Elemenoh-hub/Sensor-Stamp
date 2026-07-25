-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room reads these reflectively when mapping query results onto data classes,
# so the field names have to survive shrinking.
-keep class com.sensorstamp.openwifi.data.** { *; }

# osmdroid instantiates tile sources and overlay internals by name in places,
# and its cache/config layer uses reflection. Keeping it whole costs little and
# avoids a map that silently renders blank in a release build.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# osmdroid pulls in optional dependencies it can work without.
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
