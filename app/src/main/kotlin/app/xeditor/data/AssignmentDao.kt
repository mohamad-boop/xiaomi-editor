package app.xeditor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments")
    fun observeAll(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments")
    suspend fun getAll(): List<Assignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: Assignment)

    @Query("DELETE FROM assignments WHERE pkg = :pkg AND activity = :activity")
    suspend fun delete(pkg: String, activity: String)

    @Query("DELETE FROM assignments")
    suspend fun clear()
}
