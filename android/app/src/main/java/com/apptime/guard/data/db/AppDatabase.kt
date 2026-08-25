package com.apptime.guard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apptime.guard.core.model.AuditLog
import com.apptime.guard.core.model.CategoryOverride
import com.apptime.guard.core.model.QuotaState
import com.apptime.guard.core.model.Rule
import com.apptime.guard.core.model.TempRelaxation

@Database(
    entities = [
        Rule::class,
        QuotaState::class,
        AuditLog::class,
        CategoryOverride::class,
        TempRelaxation::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun quotaDao(): QuotaDao
    abstract fun auditDao(): AuditDao
    abstract fun categoryDao(): CategoryDao
    abstract fun relaxationDao(): RelaxationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apptime.db"
                ).build().also { instance = it }
            }
    }
}
