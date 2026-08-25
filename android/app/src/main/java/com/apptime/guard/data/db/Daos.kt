package com.apptime.guard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apptime.guard.core.model.AuditLog
import com.apptime.guard.core.model.CategoryOverride
import com.apptime.guard.core.model.QuotaState
import com.apptime.guard.core.model.Rule
import com.apptime.guard.core.model.TempRelaxation
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC")
    fun observeEnabled(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC")
    suspend fun getEnabled(): List<Rule>

    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun getAll(): List<Rule>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): Rule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface QuotaDao {
    @Query("SELECT * FROM quota_states WHERE date = :date")
    suspend fun getByDate(date: String): QuotaState?

    @Query("SELECT * FROM quota_states")
    suspend fun getAll(): List<QuotaState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: QuotaState)

    @Query("DELETE FROM quota_states WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(log: AuditLog): Long

    @Query("SELECT * FROM audit_logs ORDER BY ts DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<AuditLog>

    @Query("SELECT * FROM audit_logs WHERE ts >= :from ORDER BY ts DESC")
    fun observeSince(from: Long): Flow<List<AuditLog>>

    @Query("DELETE FROM audit_logs WHERE ts < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category_overrides")
    suspend fun getAll(): List<CategoryOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: CategoryOverride)

    @Query("DELETE FROM category_overrides WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface RelaxationDao {
    @Query("SELECT * FROM temp_relaxations")
    suspend fun getAll(): List<TempRelaxation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relax: TempRelaxation)

    @Query("DELETE FROM temp_relaxations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM temp_relaxations WHERE endElapsed < :now")
    suspend fun deleteExpired(now: Long)
}
