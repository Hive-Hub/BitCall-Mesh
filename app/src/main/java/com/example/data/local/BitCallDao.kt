package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserIdentityDao {
    @Query("SELECT * FROM user_identity LIMIT 1")
    fun getUserIdentity(): Flow<UserIdentityEntity?>

    @Query("SELECT * FROM user_identity LIMIT 1")
    suspend fun getUserIdentitySync(): UserIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserIdentity(identity: UserIdentityEntity)

    @Query("DELETE FROM user_identity")
    suspend fun clearIdentity()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY displayName ASC")
    fun getAllActiveContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE bitcallId = :bitcallId LIMIT 1")
    suspend fun getContactById(bitcallId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE bitcallId = :bitcallId LIMIT 1")
    fun observeContactById(bitcallId: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE bitcallId = :bitcallId")
    suspend fun setBlockedStatus(bitcallId: String, blocked: Boolean)

    @Query("UPDATE contacts SET isVerified = :verified WHERE bitcallId = :bitcallId")
    suspend fun setVerificationStatus(bitcallId: String, verified: Boolean)

    @Query("UPDATE contacts SET lastSeen = :lastSeen WHERE bitcallId = :bitcallId")
    suspend fun updateLastSeen(bitcallId: String, lastSeen: Long)

    @Query("SELECT * FROM contacts WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findContactByFingerprint(fingerprint: String): ContactEntity?

    @Query("UPDATE contacts SET lastHandshakeTimestamp = :timestamp, sessionCounter = sessionCounter + 1 WHERE bitcallId = :bitcallId")
    suspend fun recordEncryptionHandshake(bitcallId: String, timestamp: Long)

    @Query("UPDATE contacts SET meshRoutingTrust = :trustLevel WHERE bitcallId = :bitcallId")
    suspend fun updateMeshRoutingTrust(bitcallId: String, trustLevel: Int)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)

    @Query("SELECT * FROM chat_messages WHERE status = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getQueuedMessages(): List<ChatMessageEntity>
}

@Dao
interface MeshQueueDao {
    @Query("SELECT * FROM mesh_queue WHERE isDelivered = 0 AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getPendingPackets(now: Long): List<MeshQueueEntity>

    @Query("SELECT * FROM mesh_queue WHERE isDelivered = 0")
    fun observePendingPackets(): Flow<List<MeshQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueuePacket(packet: MeshQueueEntity)

    @Query("UPDATE mesh_queue SET isDelivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("UPDATE mesh_queue SET retryCount = retryCount + 1, lastAttemptAt = :attemptAt WHERE messageId = :messageId")
    suspend fun recordRetry(messageId: String, attemptAt: Long)

    @Query("DELETE FROM mesh_queue WHERE expiresAt <= :now OR isDelivered = 1")
    suspend fun purgeExpiredPackets(now: Long)

    @Query("SELECT COUNT(*) FROM mesh_queue WHERE isDelivered = 0")
    fun getPendingPacketCount(): Flow<Int>
}

@Dao
interface DevicePeerDao {
    @Query("SELECT * FROM device_peers ORDER BY isConnected DESC, rssi DESC")
    fun observePeers(): Flow<List<DevicePeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: DevicePeerEntity)

    @Query("UPDATE device_peers SET isConnected = :connected WHERE deviceAddress = :address")
    suspend fun updateConnectionStatus(address: String, connected: Boolean)

    @Query("DELETE FROM device_peers WHERE lastSeen < :cutoff")
    suspend fun purgeStalePeers(cutoff: Long)
}

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun getAllCallRecords(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecordEntity)

    @Query("DELETE FROM call_records")
    suspend fun clearHistory()
}
