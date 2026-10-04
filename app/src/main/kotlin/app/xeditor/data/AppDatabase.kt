package app.xeditor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class AssignmentSourceConverter {
    @TypeConverter fun fromEnum(v: AssignmentSource): String = v.name
    @TypeConverter fun toEnum(v: String): AssignmentSource = AssignmentSource.valueOf(v)
}

@Database(entities = [Assignment::class], version = 1, exportSchema = false)
@TypeConverters(AssignmentSourceConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assignments(): AssignmentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "iconpacker.db"
            ).build().also { instance = it }
        }
    }
}
