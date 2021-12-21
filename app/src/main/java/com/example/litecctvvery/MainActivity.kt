package com.example.litecctvvery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import android.hardware.Camera.CameraInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.*
import java.io.IOException
var usingFrontCam = false

class MainActivity : AppCompatActivity() {
    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null
    private val motionDetector: MotionDetector = MotionDetector()
    private val db = DBHelper(this, null)
    private lateinit var token: String
    private lateinit var tvToken: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnSwitch: ImageButton
    private var isCameraCapturing: Boolean = false
    private lateinit var cameraHandler: Handler
    var safeToTakePicture = false
    var inPreview = true
    var frontCamID = 1
    var backCamID = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvToken = findViewById(R.id.textview_token)
        btnCapture = findViewById(R.id.button_capture)
        btnSwitch = findViewById(R.id.button_switch)
        frontCamID = frontCam(CameraInfo.CAMERA_FACING_FRONT)
        backCamID = frontCam(CameraInfo.CAMERA_FACING_BACK)


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
            tvToken.text = token
        }
        else
            generateToken()

        // Create an instance of Camera
        mCamera = getCameraInstance(backCamID)
        mCamera?.setDisplayOrientation(90)
        Log.d(TAG, "frontCAM ID = " + frontCam(CameraInfo.CAMERA_FACING_FRONT).toString())
        mPreview = mCamera?.let {
            // Create our Preview view
            CameraPreview(this, it)
        }
        safeToTakePicture = true

        // Set the Preview view as the content of our activity.
        mPreview?.also {
            val preview: FrameLayout = findViewById(R.id.camera_preview)
            preview.addView(it)
        }

        btnCapture.setOnClickListener { captureButtonEvent() }
        btnSwitch.setOnClickListener { changeCam() }

        thread {
            cameraHandler = Handler(Looper.getMainLooper())
            cameraHandler.post(object: Runnable {
                override fun run() {
                    if (isCameraCapturing && safeToTakePicture) {
                        mCamera?.takePicture(null, null, mPicture)
                        safeToTakePicture = false
                    }
                    if(usingFrontCam)
                        cameraHandler.postDelayed(this, 1500)
                    else
                        cameraHandler.postDelayed(this, 900)
                }
            })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun captureButtonEvent() {
        if (isCameraCapturing) {
            isCameraCapturing = false
            btnCapture.text = "Start"
        }
        else {
            isCameraCapturing = true
            btnCapture.text = "Stop"
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCameraInstance(id: Int): Camera? {
        return try {
            Camera.open(id) // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }

    private fun frontCam(cameraFacing: Int): Int {
        val numberOfCameras = Camera.getNumberOfCameras()
        val cameraId = 0
        for (i in 0 until numberOfCameras) {
            val info = CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == cameraFacing) {
                return i
            }
        }
        return cameraId
    }

    private fun generateToken() {
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(Request.Method.GET, URL_TOKEN_POST,
            { response ->
                Log.e(TAG, "Generated Token: $response")
                db.addToken(response)
                token = response
                tvToken.text = token
            },
            { Log.e(TAG, "Token can NOT be generated") })

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
            val matrix = Matrix()
            if(usingFrontCam)
                matrix.postRotate(-90F)
            else
                matrix.postRotate(90F)

            bitmap =  Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            val base64String = getBase64OfPhoto(bitmap)
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            sendImageToCloud(timestamp, base64String, token)
            safeToTakePicture = true
        }
        else {
            safeToTakePicture = true
        }
        mCamera?.startPreview()
    }

    private fun changeCam() {
        safeToTakePicture = false
        if(inPreview)
            mCamera?.stopPreview()
        mCamera?.release()

        if(usingFrontCam){
            mCamera = getCameraInstance(backCamID)
            usingFrontCam = false
        }
        else {
            mCamera = getCameraInstance(frontCamID)
            usingFrontCam = true
        }
        mCamera?.setDisplayOrientation(90)
        mPreview = mCamera?.let {
            CameraPreview(this, it)
        }
        mPreview?.also {
            val preview: FrameLayout = findViewById(R.id.camera_preview)
            preview.addView(it)
        }
        safeToTakePicture = true
    }


    companion object {
        private const val TAG = "Main"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val IMAGE_CAPTURE_WIDTH = 300
        private const val IMAGE_CAPTURE_HEIGHT = 300
        private const val URL_IMAGE_POST = "http://128.199.123.139:8080/image/"
        private const val URL_TOKEN_POST = "http://128.199.123.139:8080/tokenCheck/"
    }
}

class CameraPreview (context: Context, private val mCamera: Camera) : SurfaceView(context), SurfaceHolder.Callback {
    private val mHolder: SurfaceHolder = holder.apply {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        addCallback(this@CameraPreview)
        // deprecated setting, but required on Android versions prior to 3.0
        setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        mCamera.apply {
            try {
                setPreviewDisplay(holder)
                startPreview()
            } catch (e: IOException) {
                Log.d(TAG, "Error setting camera preview: ${e.message}")
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.surface == null) {
            // preview surface does not exist
            return
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview()
        } catch (e: Exception) {
            // ignore: tried to stop a non-existent preview
        }

        // set focus on camera

        if(!usingFrontCam)
        try {
            setFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
        }
        catch (e: RuntimeException){
            Log.d("RTE", e.toString())
        }


        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        mCamera.apply {
            try {
                setPreviewDisplay(mHolder)
                startPreview()
            } catch (e: Exception) {
                Log.d(TAG, "Error starting camera preview: ${e.message}")
            }
        }
    }

    /**
     * Set focus on camera
     */
    private fun setFocus(mParameter: String) {
        val mParameters = mCamera.parameters
        mParameters.focusMode = mParameter
        mCamera.parameters = mParameters
    }

    companion object {
        const val TAG = "Preview"
    }

}