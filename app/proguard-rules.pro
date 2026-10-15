# Kotlinx serialization keeps generated serializers via @Serializable; nothing extra required
# for the classes used here because we only serialize our own @Serializable types.
-keepattributes *Annotation*, InnerClasses
