# R8 rules for the Kharcha release build.
#
# Room, Hilt, WorkManager, Glance and kotlinx.serialization each ship consumer rules inside their
# artifacts (RoomDatabase subclasses, Hilt entry points, worker class names, ActionCallback
# subclasses, Companion/serializer() lookups), and AGP keeps every class the manifest names.
# The rules below spell out what this app relies on at runtime so that a library dropping or
# changing its bundled rules cannot silently break a release.

# Readable stack traces (mapping.txt is attached to every GitHub release for retrace).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations and nesting metadata read at runtime by Room, Hilt and kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod

# --- Room -----------------------------------------------------------------------------------------
# Room.databaseBuilder() locates the generated implementation with Class.forName("<Database>_Impl"),
# so the database class and its generated implementation must keep their names.
-keepnames class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { void <init>(); }
-dontwarn androidx.room.paging.**

# Enums are persisted by name (Room columns, DataStore keys, backup JSON): keep the constant names.
-keepclassmembers enum com.attiqtherehman.kharcha.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --- Hilt / WorkManager ---------------------------------------------------------------------------
# Generated components are referenced directly. Hilt-injected workers are created by
# HiltWorkerFactory from a map keyed by the worker's class name.
-keepnames @androidx.hilt.work.HiltWorker class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(...);
}

# --- Glance (home-screen widgets) -----------------------------------------------------------------
# Receivers are declared in the manifest. ActionCallback implementations are instantiated by
# reflection from the class name stored in the widget's PendingIntent, and Glance records provider
# class names in its own DataStore to map widgets back to their GlanceAppWidget.
-keep public class * extends androidx.glance.appwidget.action.ActionCallback
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
-keepclassmembers class * extends androidx.glance.appwidget.protobuf.GeneratedMessageLite {
    <fields>;
}

# --- kotlinx.serialization ------------------------------------------------------------------------
# Backups (manifest.json, tables/*.json, settings.json), DataStore JSON blobs and type-safe
# navigation routes are @Serializable. The compiler plugin generates the serializers; keep the
# Companion / INSTANCE / serializer() entry points that serializer<T>() resolves at runtime.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class com.attiqtherehman.kharcha.** {
    static ** Companion;
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# --- fastexcel (XLSX export) ----------------------------------------------------------------------
# XlsxExporterSelector probes for the library with Class.forName before using it.
-keepnames class org.dhatim.fastexcel.Workbook
