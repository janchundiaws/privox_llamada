package com.futura.privox_app.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class AuthService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val BASE_URL = Constants.URL_API
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val prefs: SharedPreferences = context.getSharedPreferences("privox_prefs", Context.MODE_PRIVATE)

    suspend fun createAutomaticUser(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val existingUsername = prefs.getString("username", "") ?: ""
            if (existingUsername.isNotEmpty()) {
                return@withContext Result.success(existingUsername)
            }

            val timestamp = System.currentTimeMillis() * 1000 // To match microseconds format approx
            val suffix = String.format("%04d", Random.nextInt(1000000))
            val username = "${timestamp}_$suffix"
            val displayName = "Usuario $suffix"

            val jsonBody = """
                {
                    "username": "$username",
                    "displayName": "$displayName"
                }
            """.trimIndent()

            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/auth/register" else "$BASE_URL/api/auth/register"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val map = gson.fromJson(responseData, Map::class.java) as Map<*, *>
                    val userMap = map["user"] as? Map<*, *>

                    if (userMap != null) {
                        val deviceId = userMap["deviceId"]?.toString() ?: ""
                        prefs.edit()
                            .putString("username", username)
                            .putString("displayName", displayName)
                            .putString("deviceId", deviceId)
                            .apply()
                        return@withContext Result.success(username)
                    }
                }
                return@withContext Result.failure(Exception("Failed to register: ${response.code}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun login(username: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = prefs.getString("deviceId", "") ?: ""
            
            if (deviceId.isEmpty() && username != "admin") {
                return@withContext Result.failure(Exception("El username no fue creado en este dispositivo."))
            }

            val loginDeviceId = if (username == "admin") "deviceAdmin" else deviceId

            val jsonBody = """
                {
                    "username": "$username",
                    "deviceId": "$loginDeviceId"
                }
            """.trimIndent()

            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/auth/login" else "$BASE_URL/api/auth/login"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string()
                if (response.isSuccessful) {
                    val map = gson.fromJson(responseData, Map::class.java) as Map<*, *>
                    val token = map["token"]?.toString()
                    val userMap = map["user"] as? Map<*, *>
                    val userId = userMap?.get("id")?.toString()
                    val displayName = userMap?.get("displayName")?.toString()

                    prefs.edit().apply {
                        if (token != null) putString("token", token)
                        putString("username", username)
                        if (userId != null) putString("userId", userId)
                        if (displayName != null) putString("displayName", displayName)
                    }.apply()

                    return@withContext Result.success(username)
                } else {
                    val errorMap = gson.fromJson(responseData, Map::class.java) as? Map<*, *>
                    val errorMsg = errorMap?.get("error")?.toString() ?: "Login failed"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun getUsers(): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/users/usersaccount" else "$BASE_URL/api/users/usersaccount"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val body = gson.fromJson(responseData, Map::class.java) as Map<*, *>
                    val usersList = body["users"] as? List<*> ?: emptyList<Any>()
                    
                    val contacts = mutableListOf<Map<String, String>>()
                    val myUsername = prefs.getString("username", "") ?: ""

                    for (u in usersList) {
                        if (u is Map<*, *>) {
                            val id = u["_id"]?.toString() ?: ""
                            val userId = u["userId"]?.toString() ?: ""
                            val username = u["username"]?.toString() ?: u["displayName"]?.toString() ?: ""
                            if (userId.isNotEmpty() && username.isNotEmpty() && username != myUsername) {
                                contacts.add(mapOf("id" to id, "userId" to userId, "username" to username))
                            }
                        }
                    }
                    return@withContext Result.success(contacts)
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch users: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun getUsersToAdd(): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/users/usersadd" else "$BASE_URL/api/users/usersadd"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val body = gson.fromJson(responseData, Map::class.java) as Map<*, *>
                    val usersList = body["users"] as? List<*> ?: emptyList<Any>()
                    
                    val contacts = mutableListOf<Map<String, String>>()
                    for (u in usersList) {
                        if (u is Map<*, *>) {
                            val id = u["_id"]?.toString() ?: ""
                            val userId = u["userId"]?.toString() ?: ""
                            val username = u["username"]?.toString() ?: ""
                            val displayName = u["displayName"]?.toString() ?: ""
                            if (userId.isNotEmpty()) {
                                contacts.add(mapOf("id" to id, "userId" to userId, "username" to username, "displayName" to displayName))
                            }
                        }
                    }
                    return@withContext Result.success(contacts)
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch users to add: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun getRequests(direction: String): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/requests?direction=$direction&status=pending" else "$BASE_URL/api/requests?direction=$direction&status=pending"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val body = gson.fromJson(responseData, Map::class.java) as Map<*, *>
                    val requestsList = body["requests"] as? List<*> ?: emptyList<Any>()
                    
                    val requests = mutableListOf<Map<String, String>>()
                    val targetField = if (direction == "outgoing") "to" else "from"
                    
                    for (req in requestsList) {
                        if (req is Map<*, *>) {
                            val requestId = req["_id"]?.toString() ?: ""
                            val targetUser = req[targetField] as? Map<*, *>
                            if (targetUser != null && requestId.isNotEmpty()) {
                                val userId = targetUser["userId"]?.toString() ?: ""
                                val username = targetUser["username"]?.toString() ?: ""
                                val displayName = targetUser["displayName"]?.toString() ?: ""
                                requests.add(mapOf("requestId" to requestId, "userId" to userId, "username" to username, "displayName" to displayName))
                            }
                        }
                    }
                    return@withContext Result.success(requests)
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch requests: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun createRequest(toUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/requests" else "$BASE_URL/api/requests"
            val jsonBody = """
                {
                    "to": "$toUserId",
                    "meta": {}
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(jsonBody.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success(true)
                } else {
                    val errorMap = gson.fromJson(response.body?.string(), Map::class.java) as? Map<*, *>
                    val errorMsg = errorMap?.get("error")?.toString() ?: "Failed to create request"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun updateRequestStatus(requestId: String, status: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/requests/$requestId" else "$BASE_URL/api/requests/$requestId"
            val jsonBody = """
                {
                    "status": "$status"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .patch(jsonBody.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success(true)
                } else {
                    val errorMap = gson.fromJson(response.body?.string(), Map::class.java) as? Map<*, *>
                    val errorMsg = errorMap?.get("error")?.toString() ?: "Failed to update request"
                    return@withContext Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun removeContact(targetUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val token = prefs.getString("token", "") ?: ""
            val url = if (BASE_URL.endsWith("/")) "${BASE_URL}api/requests/contact/$targetUserId" else "$BASE_URL/api/requests/contact/$targetUserId"
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "GhoxClient/1.0")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success(true)
                } else {
                    val errorMsg = response.body?.string() ?: "Error code: ${response.code}"
                    return@withContext Result.failure(Exception("Failed to remove contact: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    fun logout() {
        prefs.edit()
            .remove("token")
            .remove("username")
            .remove("userId")
            .remove("displayName")
            .apply()
    }
}
