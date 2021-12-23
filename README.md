# SuperLiteCCTV

Super lightweight CCTV. Convert your android devices into CCTV. 

There's also better version for android with API Level 21+ <a href="https://github.com/ADryInkCartridge/LiteCCTV">LiteCCTV</a>

## How does this application work?
This is how the application works.
1. Application captures image from camera every 0.9 second.
2. If there's motion detected from the image, the application sends the image to <a href="https://github.com/ADryInkCartridge/LiteCCTVAPI">LiteCCTV API Web Server</a>.
3. The API Web Server will analyze the image for face recognition and emotion prediction.

## References
1. <a href="https://developer.android.com/reference/android/hardware/Camera">Hardware Camera Android Documentation (Deprecated)</a>
2. <a href="https://developer.android.com/training/volley">Volley Library Documentation</a>
3. <a href="https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.concurrent/thread.html">Thread in Kotlin</a>
