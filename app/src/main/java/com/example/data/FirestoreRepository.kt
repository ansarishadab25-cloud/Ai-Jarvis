package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        val docRef = db.collection("users").document(userId)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String?,
        defaultLanguage: String,
        handsFreeEnabled: Boolean,
        historyEnabled: Boolean,
        speechRate: Double
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(userId)
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val safeLang = if (defaultLanguage in ALLOWED_LANGUAGES) defaultLanguage else "hi-IN"
                val safeRate = speechRate.coerceIn(0.5, 2.0)
                val safeName = displayName?.trim()?.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredLanguageCode" to safeLang,
                    "handsFreeEnabled" to handsFreeEnabled,
                    "historyEnabled" to historyEnabled,
                    "speechRate" to safeRate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        userId: String,
        preferredLanguageCode: String? = null,
        handsFreeEnabled: Boolean? = null,
        historyEnabled: Boolean? = null,
        speechRate: Double? = null
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user update." }
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (preferredLanguageCode != null) {
                require(preferredLanguageCode in ALLOWED_LANGUAGES) { "Invalid language code" }
                updates["preferredLanguageCode"] = preferredLanguageCode
            }
            if (handsFreeEnabled != null) {
                updates["handsFreeEnabled"] = handsFreeEnabled
            }
            if (historyEnabled != null) {
                updates["historyEnabled"] = historyEnabled
            }
            if (speechRate != null) {
                updates["speechRate"] = speechRate.coerceIn(0.5, 2.0)
            }
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeCommandHistory(userId: String): Flow<List<CloudCommandRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user list." }
        val path = "users/$userId/commands"
        db.collection("users")
            .document(userId)
            .collection("commands")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudCommandRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun addCommandRecord(
        userId: String,
        query: String,
        response: String,
        intentType: String,
        language: String,
        status: String
    ): Result<Unit> {
        var docPath = "users/$userId/commands"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeQuery = query.trim().take(1000).ifBlank { "-" }
            val safeResponse = response.trim().take(2000).ifBlank { "-" }
            val safeIntent = intentType.trim().take(64).ifBlank { "Chat" }
            val safeLanguage = if (language in ALLOWED_LANGUAGES) language else "hi-IN"
            val safeStatus = if (status in ALLOWED_STATUSES) status else "COMPLETED"

            val docRef = db.collection("users").document(userId).collection("commands").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "query" to safeQuery,
                "response" to safeResponse,
                "intentType" to safeIntent,
                "language" to safeLanguage,
                "status" to safeStatus,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCommandRecord(userId: String, commandId: String): Result<Unit> {
        val docPath = "users/$userId/commands/$commandId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(commandId.isNotBlank()) { "Command ID must not be blank" }
            db.collection("users")
                .document(userId)
                .collection("commands")
                .document(commandId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllCommands(userId: String): Result<Unit> {
        val path = "users/$userId/commands"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user batch delete." }
            val snapshot = db.collection("users")
                .document(userId)
                .collection("commands")
                .get()
                .await()
            val chunks = snapshot.documents.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        private val ALLOWED_LANGUAGES = setOf("hi-IN", "hi-en", "en-IN")
        private val ALLOWED_STATUSES = setOf("COMPLETED", "FAILED", "CONFIRMED", "CANCELLED")
    }
}
