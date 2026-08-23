# Tambahkan aturan ProGuard khusus di sini.
-keep class com.example.posapp.data.local.entity.** { *; }
-keep class com.dantsu.escposprinter.** { *; }

# Apache POI mereferensikan library opsional (OSGi, Apache Batik/SVG, aQute bnd)
# yang tidak digunakan di path Android manapun di app ini. Aman untuk diabaikan.
-dontwarn org.osgi.framework.**
-dontwarn org.apache.batik.**
-dontwarn aQute.bnd.annotation.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.poi.**

# StAX API (javax.xml.stream) tidak tersedia di runtime Android,
# hanya dipakai secara opsional oleh org.apache.xmlbeans (dependency Apache POI)
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.**

# Saxon XPath engine (net.sf.saxon) — dependency opsional xmlbeans,
# tidak dipakai oleh jalur kode Apache POI yang digunakan di app ini
-dontwarn net.sf.saxon.**
