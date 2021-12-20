package com.example.litecctvvery

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.media.RingtoneManager
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private var TAG = "Main"
    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null
    private val motionDetector: MotionDetector = MotionDetector()
    private val db = DBHelper(this, null)
    private lateinit var token: String
    private lateinit var tvToken: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvToken = findViewById(R.id.textview_token)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Token generation
        val db = DBHelper(this, null)
        val cursor = db.getToken()
        cursor!!.moveToFirst()
        if(cursor.count != 0 ){
            token = cursor.getString(0)
            tvToken.setText(token)
        }
        else
            generateToken()

        // Create an instance of Camera
        mCamera = getCameraInstance()
        mCamera?.setDisplayOrientation(90)
        mPreview = mCamera?.let {
            // Create our Preview view

            CameraPreview(this, it)
        }

        // Set the Preview view as the content of our activity.
        mPreview?.also {
            val preview: FrameLayout = findViewById(R.id.camera_preview)
            preview.addView(it)
        }

        var captureButton: Button = findViewById(R.id.button_capture)
        captureButton.setOnClickListener { captureButtonEvent() }
    }

    private fun captureButtonEvent() {
        thread() {
            mCamera?.takePicture(null, null, mPicture)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun releaseCamera() {
        mCamera?.release() // release the camera for other applications
        mCamera = null
    }

    fun getCameraInstance(): Camera? {
        return try {
            Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }

    private fun generateToken() {
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(Request.Method.GET, URL_TOKEN_POST,
            Response.Listener<String> { response ->
                Log.e(TAG, "Generated Token: $response")
                db.addToken(response)
                token = response
                tvToken.setText(token)
            },
            Response.ErrorListener { Log.e(TAG, "Token can NOT be generated") })

        queue.add(stringRequest)
    }

    private fun playSound() {
        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun getBase64OfPhoto(image: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val byteArray: ByteArray = baos.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun sendImageToCloud(fileName: String, imageData: String?, token: String) {
        MySingleton.getInstance(this.applicationContext).requestQueue
        val map = mutableMapOf<String, Any?>()
        map["filename"] = fileName
        map["token"] = token
        map["imagedata"] = imageData

        val json = JSONObject(map)
        Log.e("sendImageToCloud", json.toString() )
        val jsonReq = object: JsonObjectRequest(
            Method.POST,URL_IMAGE_POST,json,
            Response.Listener { response ->
                Log.i(TAG, "Response from server: $response")
            }, Response.ErrorListener{
                // Error in request
                Toast.makeText(this,
                    "Volley error: $it",
                    Toast.LENGTH_SHORT).show()
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/json; charset=utf-8"
                return headers
            }
        }

        jsonReq.retryPolicy = DefaultRetryPolicy(
            0,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        MySingleton.getInstance(this).addToRequestQueue(jsonReq)
    }

    private val mPicture = Camera.PictureCallback { data, _ ->
        var bitmap: Bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        if (motionDetector.hasMotion(bitmap)) {
            Toast.makeText(baseContext, "MOTION DETECTED - Sending Picture To Server Now", Toast.LENGTH_LONG).show()

            // Play sound
            playSound()

            // Resize bitmap
            bitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_CAPTURE_WIDTH, IMAGE_CAPTURE_HEIGHT, false)

            val base64String = getBase64OfPhoto(bitmap)
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            sendImageToCloud(timestamp, base64String, token)
        }
        else {
            Toast.makeText(baseContext, "NO MOTION DETECTED", Toast.LENGTH_LONG).show()
        }
        mCamera?.startPreview()
    }


    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val IMAGE_CAPTURE_WIDTH = 300
        private const val IMAGE_CAPTURE_HEIGHT = 300
        private const val URL_IMAGE_POST = "http://128.199.123.139:8080/image/"
        private const val URL_TOKEN_POST = "http://128.199.123.139:8080/tokenCheck/"
    }
}