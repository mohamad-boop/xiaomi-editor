package app.xeditor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssignmentSource { AUTO, MANUAL, SKIP }

@Entity(tableName = "assignments", primaryKeys = ["pkg", "activity"])
data class Assignment(
    val pkg: String,
    val activity: String,
    val drawable: String?,
    val source: AssignmentSource,
    val iconPackPackage: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
