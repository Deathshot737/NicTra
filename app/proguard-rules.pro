# Mantener las entidades de Room para que no se cambien los nombres de las tablas y campos
-keepclassmembers class com.nictra.data.local.entities.** { *; }
-keep class com.nictra.data.local.entities.** { *; }

# Mantener los DAOs
-keep interface com.nictra.data.local.dao.** { *; }

# Mantener las clases de Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
