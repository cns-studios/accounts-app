# WorkManager (update checks) builds its Room database by reflection: R8 must keep the generated
# *_Impl classes and their no-argument constructors, or the app crashes at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
