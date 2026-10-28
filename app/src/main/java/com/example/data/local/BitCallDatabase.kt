package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserIdentityEntity::class,
        ContactEntity::class,
        ChatMessageEntity::class,
        MeshQueueEntity::class,
        DevicePeerEntity::class,
        CallRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BitCallDatabase : RoomDatabase() {
    abstract fun userIdentityDao(): UserIdentityDao
    abstract fun contactDao(): ContactDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun meshQueueDao(): MeshQueueDao
    abstract fun devicePeerDao(): DevicePeerDao
    abstract fun callRecordDao(): CallRecordDao

    companion object {
        @Volatile
        private var INSTANCE: BitCallDatabase? = null

        fun getInstance(context: Context): BitCallDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitCallDatabase::class.java,
                    "bitcall_secure.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
