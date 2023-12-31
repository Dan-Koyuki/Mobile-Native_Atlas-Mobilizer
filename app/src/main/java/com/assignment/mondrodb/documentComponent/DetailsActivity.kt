package com.assignment.mondrodb.documentComponent

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.assignment.mondrodb.R
import com.assignment.mondrodb.myAdapter.DocumentAdapter
import com.assignment.mondrodb.myModel.DocumentDetails
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DetailsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Activity"
    }

    private lateinit var requestQueue: RequestQueue
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var vAuth : FirebaseAuth
    private var vDocument: Map<String, String>? = null
    private lateinit var vView : RecyclerView
    private lateinit var vAdapter : DocumentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        vAuth = FirebaseAuth.getInstance()

        requestQueue = Volley.newRequestQueue(this)
        vView = findViewById(R.id.rvDocumentDetails)

        vView.setHasFixedSize(true)
        vView.layoutManager = LinearLayoutManager(this)

        btnHandler()

        getDocument()

    }

    private fun setupAdapter(documentMap: Map<String, String>) {
        vAdapter = DocumentAdapter(documentMap)
        vView.adapter = vAdapter
    }

    private fun getUserId(): String {
        val currentUser = vAuth.currentUser
        return currentUser?.uid.toString()
    }

    private suspend fun makeApiCallWithContext(apiUrl: String, jsonObject: JSONObject): String {
        return suspendCancellableCoroutine { continuation ->
            val request = JsonObjectRequest(
                Request.Method.POST, apiUrl, jsonObject,
                { response ->
                    // Instead of trying to parse the response as JSONObject, handle it as String
                    continuation.resume(response.toString())
                },
                { error ->
                    continuation.resumeWithException(error)
                })

            requestQueue.add(request)
            continuation.invokeOnCancellation {
                requestQueue.cancelAll(TAG) // Cancel the request if coroutine is cancelled
            }
        }
    }

    private fun getDocument(){
        coroutineScope.launch {
            val apiUrl = "https://mongo-db-api-coral.vercel.app/documents/select"

            val jsonObject = JSONObject()
            jsonObject.put("userId", getUserId())
            jsonObject.put("pID", intent.getStringExtra("DocumentId"))

            try {
                val response = withContext(Dispatchers.IO){
                    makeApiCallWithContext(apiUrl, jsonObject)
                }

                Log.d("One Document API", response)

                if (response.isNotEmpty()){
                    // due to continuation.resume(response.toString()) in the makeApiCallWithContext,
                    // the response is String version from JSON object of a Document from MongoDB
                    // vList is expecting DocumentDetails as element of its array
                    // DocumentDetails expecting vMap: Map<String, Any>
                    // Parse the JSON response string into a JSONObject
                    val gson = Gson()
                    val documentResponse = gson.fromJson(response, DocumentDetails::class.java)

                    val documentMap = documentResponse.document
                    vDocument = documentMap

                    vDocument?.let { setupAdapter(it) }

                } else {
                    Toast.makeText(this@DetailsActivity, "Failed, Please Check Your Connection or Database Permission!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.d("APIError", "Error: $e")
            }
        }
    }

    private fun remove(){
        coroutineScope.launch {
            val apiUrl = "https://mongo-db-api-coral.vercel.app/documents/remove"

            val jsonObject = JSONObject()
            jsonObject.put("userId", getUserId())
            jsonObject.put("pID", intent.getStringExtra("DocumentId"))

            try {
                val response = withContext(Dispatchers.IO) {
                    makeApiCallWithContext(apiUrl, jsonObject)
                }

                // Handle success response
                if (response.contains("Document Removed!")) {
                    Toast.makeText(this@DetailsActivity, "Removed!!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@DetailsActivity, "Unexpected Response,Try Again Later!", Toast.LENGTH_SHORT).show()
                    // Handle unexpected response
                    Log.d("Document Remove Error", "Unexpected response: $response")
                }
            } catch (e: Exception) {
                // Handle error
                Toast.makeText(this@DetailsActivity, "Error: Check your Connection or Role Permission", Toast.LENGTH_SHORT).show()
                Log.d("Document Remove Error", "Error: ${e.message}")
            }
        }
    }

    private fun btnHandler(){
        // Back button logic, intent to DocumentActivity and delete this activity history
        val backBtn = findViewById<ImageView>(R.id.ivBackBtn)
        backBtn.setOnClickListener {
            finish()
        }

        //Remove Button
        val removeBtn = findViewById<ImageView>(R.id.tvRemoveDocumentBtn)
        removeBtn.setOnClickListener {
            remove()
            finish()
        }

        //Update Button
//        val updateBtn = findViewById<Button>(R.id.btnUpdate)
//        updateBtn.setOnClickListener {
//            Toast.makeText(this@DetailsActivity, "Coming Soon!", Toast.LENGTH_SHORT).show()
//        }
    }

}