package ctandroid;

/*
Copyright 2016-2017 Cycronix

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License.
*/
 
/*
 * CloudTurbine Audio/Video Capture Utility
 * Matt Miller
 * Cycronix
 * 8/28/2015
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

import cycronix.ctlib.*;

import com.cycronix.ctandroidav.R;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;

public class CTandroidAV extends Activity {

    String sourceFolder = "AndroidAV";        // FTP remote folder (not local sdcard)
    String ftpHost = "cloudturbine.net";
    String ftpUser = "cloudturbine";
    String ftpPassword = "rbnb";
    int rateIndex = 1;                                    // slow, medium, fast msec intervals (rateIndex is 0-based)
    int[] audioRateList = {5000, 2000, 1000, 500, 200};            // msec intervals
    int[] videoRateList = {200, 100, 100, 100, 100};
    int avMode = 0;        // avmode: 0-avboth, 1-video-only, 2-audio-only

    int frequency = 8000;                //8000, 22050, 44100
    int quality = 80;                    // camera quality 10-100
    private int cameraId = 1;            // 0=front, 1=back
    private int imageRes = 0;            // 0=lowres 320x240, 1=hires 640x480

    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    collectAudio recordTask;
    short threshold = 0;        // was 200

    boolean debug = false;
    boolean waveFormat = true;                // output with WAV headers (not fully debugged)
    boolean Running = false;

    final static String TAG = "CTandroidAV";
    int nmax = 1000000;                        // fail-safe quit
    int flushDur = 0;

    TextView tv;
    CTftp ctw = null;                    // separate CTwriters audio/video (async FTP)
//	CTremote.remoteSelect remoteMode = CTremote.remoteSelect.FTP;			// FILE, DROPBOX, FTP

    private Camera mCamera = null;
    private CameraPreview mPreview;
    private LimitedQueue<TimeValue> imageQueue;     // queue of images ready to store
    private int maxQ = 20;                           // max queue length

    private Display mDisplay = null;
    private long bytesWritten = 0;
    private Activity thisActivity = this;
    SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (debug) Log.i(TAG, "source folder: " + sourceFolder);

        // get saved preferences
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        ftpHost = sharedPref.getString("Host", ftpHost);
        ftpUser = sharedPref.getString("User", ftpUser);
        ftpPassword = sharedPref.getString("Password", ftpPassword);
        sourceFolder = sharedPref.getString("Folder", sourceFolder);
        rateIndex = sharedPref.getInt("Rate", rateIndex);
        cameraId = sharedPref.getInt("CameraId", cameraId);
        imageRes = sharedPref.getInt("imageRes", imageRes);
        avMode = sharedPref.getInt("avMode", imageRes);

        // buttons
        final Button btnStart = (Button) findViewById(R.id.Start);
        final Button btnCam = (Button) findViewById(R.id.CameraId);
        final Button btnImage = (Button) findViewById(R.id.Image);
        final Button btnQuit = (Button) findViewById(R.id.Quit);
        final Button btnAVmode = (Button) findViewById(R.id.AVmode);

        ((TextView) findViewById(R.id.Host)).setText(ftpHost);
        ((TextView) findViewById(R.id.User)).setText(ftpUser);
        ((TextView) findViewById(R.id.Password)).setText(ftpPassword);
        ((TextView) findViewById(R.id.Folder)).setText(sourceFolder);
        if (cameraId == 0) btnCam.setText("Back");
        else btnCam.setText("Front");
        if (imageRes == 0) btnImage.setText("LoRes");
        else btnImage.setText("HiRes");
        if (avMode == 1) btnAVmode.setText("Video");
        else if (avMode == 2) btnAVmode.setText("Audio");
        else btnAVmode.setText("AV");

        if (rateIndex < 0 || rateIndex >= videoRateList.length)
            rateIndex = 1;            // rateIndex is 0-based
        ((RadioButton) ((RadioGroup) findViewById(R.id.radioGroup1)).getChildAt(rateIndex + 1)).setChecked(true);        // radioGroup buttons start at index=1

        WindowManager mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();

//		allocate();		// allocate camera and view resources

        tv = (TextView) findViewById(R.id.Status);

//		final collectAudio ca = new collectAudio();		// start async thread, run/pause on Running flag
//		ca.execute();

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
        radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioGroup rg = ((RadioGroup) findViewById(R.id.radioGroup1));
                rateIndex = rg.indexOfChild(rg.findViewById(checkedId)) - 1;            // radioGroup index0 offset by "Interval" label
//	    		Log.d(TAG,"-----------------rateIndex: "+rateIndex);
            }
        });

        btnStart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collectAudio ca = new collectAudio();
                if (!Running) {
                    getInputs();
                    Toast.makeText(CTandroidAV.this, "Writing to " + sourceFolder + "...", Toast.LENGTH_SHORT).show();
                    Running = true;
                    btnStart.setText("Pause");
                    allocate();
                    ca.execute();        // start async thread
                } else {
                    Toast.makeText(CTandroidAV.this, "Stopping...", Toast.LENGTH_SHORT).show();
                    saveInputs();
                    Running = false;
//					ca.shutDown();
                    btnStart.setText("Start");
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                    ;         // pause for shutdown
                    thisActivity.recreate();
                }
            }
        });

        btnCam.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraId == 1) {
                    cameraId = 0;
                    btnCam.setText("Back");
                } else {
                    cameraId = 1;
                    btnCam.setText("Front");
                }
            }
        });

        btnImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (imageRes == 1) {
                    imageRes = 0;
                    btnImage.setText("LoRes");
                } else {
                    imageRes = 1;
                    btnImage.setText("HiRes");
                }
            }
        });

        btnAVmode.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (avMode == 0) {
                    avMode = 1;
                    btnAVmode.setText("Video");
                } else if (avMode == 1) {
                    avMode = 2;
                    btnAVmode.setText("Audio");
                } else {
                    avMode = 0;
                    btnAVmode.setText("AV");
                }
            }
        });

        btnQuit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(CTandroidAV.this, "Exiting...", Toast.LENGTH_SHORT).show();
                Running = false;
                saveInputs();
                if (mCamera != null) mCamera.release();
                finish();
                System.exit(0);     // actually exit dude
            }
        });

    }


    private void allocate() {
//		currentPreview = null;
        if (mCamera != null) mCamera.release();
        mCamera = getCameraInstance();
        mPreview = new CameraPreview(getApplicationContext(), mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);
    }

    private void getInputs() {
        sourceFolder = ((EditText) findViewById(R.id.Folder)).getText().toString();
        ftpHost = ((EditText) findViewById(R.id.Host)).getText().toString();
        ftpUser = ((EditText) findViewById(R.id.User)).getText().toString();
        ftpPassword = ((EditText) findViewById(R.id.Password)).getText().toString();
        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
        rateIndex = radioGroup.indexOfChild(findViewById(radioGroup.getCheckedRadioButtonId())) - 1;
    }

    // save preferences
    private void saveInputs() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("Host", ftpHost);
        editor.putString("User", ftpUser);
        editor.putString("Password", ftpPassword);
        editor.putString("Folder", sourceFolder);
        editor.putInt("Rate", rateIndex);
        editor.putInt("CameraId", cameraId);
        editor.putInt("imageRes", imageRes);
        editor.putInt("avMode", avMode);
        editor.commit();
    }

    // this needs to run Async so network IO (i.e. FTP) will run without throwing exception
    private class collectAudio extends AsyncTask<Void, Void, Void> {
        int icount = 0;
        String status = "Stopped";
        AudioRecord audioRecord = null;
        Timer timeTask = null;
        byte[] dataBuffer = null;
        int bufferSize = 0;

        collectAudio() {
        }

        @Override
        protected Void doInBackground(Void... voids) {

            // Create our Preview view and set it as the content of our activity.

            try {
                boolean recording = false;
 //               long oldtime = 0;

                while (true) {
                    long oldTime = 0;
                    if (status.equals("Stopped")) status = "Running...";
                    int blockDur = audioRateList[rateIndex];        // expected audio block duration (ms)

                    if (Running) {
                        if (recording == false) {       // startup
                            setup();
                            if (avMode != 1)
                                audioRecord.startRecording();                                            // start audio
                            if (avMode != 2) {          // not audio-only
                                if (debug) Log.i(TAG, "Starting Timer!!!!");
                                if(avMode == 1) {    // only run videoTask if video-only mode, otherwise audio task outputs Q images
                                    VideoTask videoTask = new VideoTask();
                                    new Timer().schedule(videoTask, 0, videoRateList[rateIndex] / 4);            // go 4x faster to catch up with Q
                                }
                                CameraTask cameraTask = new CameraTask(maxQ);
                                new Timer().scheduleAtFixedRate(cameraTask, 0, videoRateList[rateIndex]); // fixed rate
                            }
                            recording = true;
                        }

                        if (avMode == 1) {
                            Thread.sleep(100);            // no audio
                            continue;
                        }

                        int bufferReadResult = audioRecord.read(dataBuffer, 0, bufferSize);        // blocking read for bufferSize
  //                      long time = System.currentTimeMillis();
 //                       if (oldtime != 0) {
 //                           long dt = time - oldtime;
 //                           if (Math.abs(blockDur - dt) < 50)
 //                               time = oldtime + blockDur;            // consistent timing if close
 //                           if (debug)
 //                               Log.i(TAG, "dt: " + dt + ", blockDur: " + blockDur + ", time: " + time + ", oldtime: " + oldtime);
 //                       }
 //                       oldtime = time;

                        if (debug)
                            System.err.println("bufferLen: " + dataBuffer.length + ", bufferSize: " + bufferSize + ", blockDur: " + audioRateList[rateIndex] + "rateIndex: " + rateIndex);

                        if (AudioRecord.ERROR_INVALID_OPERATION == bufferReadResult) {
                            Log.e(TAG, "Invalid Audio Read!  Running: " + Running);
                            Running = false;
                        } else {
                            //check signal, put a threshold
                            int foundPeak = 0;
                            if (threshold > 0) {
                                foundPeak = searchThreshold(getDataAsInt16(dataBuffer), threshold);
                                if (debug) Log.i(TAG, "foundPeak: " + foundPeak);
                            }

                            if (foundPeak >= 0) {                // found signal
                                icount++;

                                tv.post(new Runnable() {
                                    public void run() {
                                        tv.setText(status);
                                    }
                                });    // can't tv.setText directly; not on same thread
                                if (debug) Log.i(TAG, status);

                                synchronized (TAG) {        // don't let an image slip in between audio put and flush
                                    long time = System.currentTimeMillis();

                                    // MJM new logic 3/28/2017:
                                    // snag images from Queue that fit in current range
                                    if(avMode != 2) {
                                        TimeValue TV;
                                        while ((TV = imageQueue.poll()) != null) {
                                            long imageTime = TV.time;
                                            if (imageTime < oldTime) continue;  // disccard too-old
                                            if (imageTime > time) break;        // out of range (save one?)
                                            ctw.setTime(imageTime);
  //                                          ctw.putData("image.jpg", jpegFromPreview(TV.value));
                                            ctw.putData("image.jpg", TV.value);     // image converted to jpeg in cameraTask
                                        }
                                    }
                                    oldTime = time;


									ctw.setTime(time);     // use running clock time, avoid inconsistent block/file times
                                    try {
 //                                       Log.d(TAG,"########about to flush, time: "+time);
                                        if (waveFormat)
                                            ctw.putData("audio.wav", addWaveHeader(dataBuffer, frequency));    // don't need blockmode, one-audio block flush per file
                                        else ctw.putData("audio.pcm", dataBuffer);
                                        if (debug) Log.i(TAG, "audio flush!");
                                        ctw.flush(true);                            // gapless flag
 //                                       Log.d(TAG,"########   done flush, time: "+time);
                                    }
                                    catch(IOException ioe) {
                                        System.err.println("FTP exception: "+ioe.getMessage());     // Log not work inside asyncTask
                                        Log.w(TAG,"FTP exception: "+ioe.getMessage());
                                            // lumber on
                                    }
                                }
 //                               ctw.flush(true);                            // gapless flag

                                bytesWritten += dataBuffer.length;
                                status = "Running: Files: " + icount + ", Total (KB): " + (int) (bytesWritten / 1024);
                            }
                        }
                    } else {
                        if (recording) {
                            status = "Stopped: Files: " + icount + ", Total (KB): " + (int) (bytesWritten / 1024);
                            tv.post(new Runnable() {
                                public void run() {
                                    tv.setText(status);
                                }
                            });
                            shutDown();
                        }
                        Thread.sleep(200);
                        return null;
                    }
                }
            } catch (Exception e) {
                System.err.println("CTandroidAV exception: "+e);
                Log.e(TAG, "Exception collectAudio: " + e);
                Toast.makeText(CTandroidAV.this, "Exception: " + e, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } finally {
                if (mCamera != null) mCamera.release();
            }

            return null;
        }

        private void shutDown() {
            try {
                if (timeTask != null) {
                    timeTask.cancel();
                    timeTask.purge();
                }
                if (debug) Log.i(TAG, "shutDown!!!!!");
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
                ctw.close();
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            } catch (Exception e) {
                Log.w(TAG, "exception on shutDown: " + e);
            }
        }

        //-----------------------------------------------------------------------------------------------------------
        // (re)setup for recording
        private void setup() throws Exception {
            try {
//				ctw = new CTremote(sourceFolder, remoteMode);
                ctw = new CTftp(sourceFolder);
                ctw.login(ftpHost, ftpUser, ftpPassword);        // need to get user/password out of hardcoded

                //			ctw.setZipMode(true);			// bundle to zip files
                ctw.setBlockMode(false, true);    // no data packing, yes zip files
                ctw.autoFlush(0);                // no autoflush, segments
                ctw.autoSegment(100);
                ctw.setDebug(debug);
                //		ctw.setBlockMode(false);		// blockmode needs to be per-channel, or new addData vs blockmode/putdata... ??

                int blockDur = audioRateList[rateIndex];                    // rateIndex is 0-based
                bufferSize = (int) (2. * frequency * (blockDur / 1000.));        // buffer bytes per blockDuration
                int minBufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
                if (bufferSize < minBufferSize) bufferSize = minBufferSize;
                dataBuffer = new byte[bufferSize];

                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
                //			timeTask = new Timer();
            } catch (Exception e) {
                Log.e(TAG, "Unable to Connect to FTP server: " + ftpHost);
                Toast.makeText(CTandroidAV.this, "Exception on setup: " + e, Toast.LENGTH_SHORT).show();
            }
        }

        //-----------------------------------------------------------------------------------------------------------
        // CameraTask simply snags preview snapshots at timer rate
        // run async from VideoTask to overlap camera-capture with IO
        class CameraTask extends TimerTask {

            CameraTask(int maxQ) {
                imageQueue = new LimitedQueue(maxQ);      // queue up to maxQ images
            }

            //			@Override public synchronized void run() {
            @Override
            public void run() {
                if (!Running || mCamera == null) {                // handled by shutDown()
                    this.cancel();
                    return;
                }

                mCamera.setOneShotPreviewCallback(new PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera camera) {
//                        currentPreview = data;
                        long t = System.currentTimeMillis();
                        imageQueue.add(new TimeValue(t, jpegFromPreview(data)));    // convert to jpeg on the fly, spread out the CPU work
   //                     imageQueue.add(new TimeValue(t, data));
                        if (debug) Log.i(TAG, "CameraTask, time: " + t);
                    }
                });
            }
        }

        //-----------------------------------------------------------------------------------------------------------
        // VideoTask grabs most recent camera snapshot and puts it to CT
        class VideoTask extends TimerTask {
            long vcount = 0;
            long modflush = 1;

            VideoTask() {
                if (avMode == 1) modflush = audioRateList[rateIndex] / videoRateList[rateIndex];
            }

            //			@Override public synchronized void run() {
            @Override
            public void run() {
                byte[] currentPreview = null;            // current value of preview image
                long currentTime = 0;                 // time of currentPreview

                if (debug) Log.i(TAG, "TimerTask, Running: " + Running);
                if (!Running || mCamera == null) {                // handled by shutDown()
                    this.cancel();
                    return;
                }

                try {
                    TimeValue tv = imageQueue.pop();
                    currentTime = tv.time;
                    currentPreview = tv.value;
                    if (debug) Log.i(TAG, "VideoTask, time: " + currentTime);
                } catch (Exception e) {
                    return;
                }       // empty queue

                if (currentPreview == null) return;        // startup

//				long time = System.currentTimeMillis();
                if (debug) Log.i(TAG, "currentPreview.length: " + currentPreview.length);

                try {
                    byte[] jpeg = jpegFromPreview(currentPreview);

                    if (debug) Log.i(TAG, "converted raw: " + currentPreview.length + " to: " + jpeg.length);

                    synchronized (TAG) {        // don't let an image slip in between audio put and flush
                        ctw.setTime(currentTime);
//						ctw.setTime(System.currentTimeMillis());			// for in-frame times, just set it here
                        ctw.putData("image.jpg", jpeg);
                    }
                    bytesWritten += jpeg.length;

                    if (avMode == 1) {
                        vcount++;
                        if ((vcount % modflush) == 0) {
                            icount++;
                            if (debug) Log.i(TAG, "video flush!");
                            ctw.flush();        // no audio, flush here
                        }
                        status = "Running: Files: " + icount + ", Total (KB): " + (int) (bytesWritten / 1024);
                        tv.post(new Runnable() {
                            public void run() {
                                tv.setText(status);
                            }
                        });    // can't tv.setText directly; not on same thread
                    }

                } catch (Exception e) {
                    Log.e(TAG, "ERROR on image conversion to JPEG");
                }
            }
        }
    }

    //-----------------------------------------------------------------------------------------------------------
    byte[] jpegFromPreview(byte[] currentPreview) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Parameters parameters = mCamera.getParameters();
        Size size = parameters.getPreviewSize();
        YuvImage image = new YuvImage(currentPreview, parameters.getPreviewFormat(), size.width, size.height, null);

        image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), quality, baos);

        byte[] jpeg = baos.toByteArray();
        float rotation = (float) 0.;
        if (cameraId == 1 && mDisplay.getRotation() == Surface.ROTATION_0)
                rotation = (float) 270.;
        else if (cameraId == 0 && mDisplay.getRotation() == Surface.ROTATION_0)
                rotation = (float) 90.;

        if (debug) Log.i(TAG, "cameraId: " + cameraId + ", getRotation: " + mDisplay.getRotation() + ", rotation: " + rotation);

        if (rotation != 0.) {
            // This is the same image as the preview but in JPEG and not rotated
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            ByteArrayOutputStream rotatedStream = new ByteArrayOutputStream();

            // Rotate the Bitmap
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            // We rotate the same Bitmap
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight(), matrix, false);

            // We dump the rotated Bitmap to the stream
            bitmap.compress(CompressFormat.JPEG, 50, rotatedStream);
            jpeg = rotatedStream.toByteArray();
            // whew
        }
        return jpeg;
    }

    //-----------------------------------------------------------------------------------------------------------
    short[] getDataAsInt16(byte[] bytes) {
        boolean swap = false;
        ByteOrder border;
        if (swap) border = ByteOrder.BIG_ENDIAN;            // Java (non-Intel) order
        else border = ByteOrder.LITTLE_ENDIAN;        // Intel order (default, most common)

        short shorts[] = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(border).asShortBuffer().get(shorts);
        return shorts;
    }

    //-----------------------------------------------------------------------------------------------------------
    int searchThreshold(short[] arr, short thr) {
        int peakIndex;
        int arrLen = arr.length;
        for (peakIndex = 0; peakIndex < arrLen; peakIndex++) {
            if ((arr[peakIndex] >= thr) || (arr[peakIndex] <= -thr)) {
                return peakIndex;
            }
        }
        return -1; //not found
    }

    //-----------------------------------------------------------------------------------------------------------
    // camera stuff

    /**
     * A safe way to get an instance of the Camera object.
     */
    public Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
        } catch (Exception e) {
            Log.e(TAG, "Error:  Camera not available!");
            // Camera is not available (in use or does not exist)
        }

        Parameters params = c.getParameters();
        params.setFlashMode(Parameters.FLASH_MODE_OFF);
        params.setPictureFormat(ImageFormat.JPEG);
        params.setJpegQuality(50);            // 1-100, 100 best
        c.setParameters(params);

        return c; // returns null if camera is unavailable
    }

    //-----------------------------------------------------------------------------------------------------------
    /**
     * A basic Camera preview class
     */
    SurfaceHolder mHolder = null;

    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        //	    private SurfaceHolder mHolder;
        private Camera pCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            pCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
//	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                pCamera.setPreviewDisplay(holder);
                pCamera.startPreview();
            } catch (IOException e) {
                Log.e(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null) {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                pCamera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here


            Parameters parameters = pCamera.getParameters();
            if (mDisplay.getRotation() == Surface.ROTATION_0) {
//	            parameters.setPreviewSize(height, width);                           
                pCamera.setDisplayOrientation(90);
            }
            if (mDisplay.getRotation() == Surface.ROTATION_90) {
//	            parameters.setPreviewSize(width, height); 
                if (debug) Log.i(TAG, "ROTATION_90!!!");
                pCamera.setDisplayOrientation(0);
            }
            if (mDisplay.getRotation() == Surface.ROTATION_180) {
//	            parameters.setPreviewSize(height, width);               
            }
            if (mDisplay.getRotation() == Surface.ROTATION_270) {
//	            parameters.setPreviewSize(width, height);
                pCamera.setDisplayOrientation(180);
            }

            if (imageRes == 0)
                parameters.setPreviewSize(320, 240);        // smaller images work better at fastest rate
            else parameters.setPreviewSize(640, 480);

            pCamera.setParameters(parameters);

            // start preview with new settings
            try {
//	        	pCamera.setDisplayOrientation(90);
                pCamera.setPreviewDisplay(mHolder);
                pCamera.startPreview();
            } catch (Exception e) {
                Log.e(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }
    }

    private byte[] addWaveHeader(byte[] dataBuffer, long longSampleRate) throws IOException {
        byte RECORDER_BPP = 16;
        long totalAudioLen = dataBuffer.length;
        long totalDataLen = totalAudioLen + 36;
        //	      long longSampleRate = frequency;
        int channels = 1;
        long byteRate = RECORDER_BPP * frequency * channels / 8;

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        byte[] waveBuffer = new byte[header.length + dataBuffer.length];
        System.arraycopy(header, 0, waveBuffer, 0, header.length);

        // try reversing byte order		NG
//		short[] sbuffer = getDataAsInt16(dataBuffer);
//		dataBuffer = ShortToByte(sbuffer);

        System.arraycopy(dataBuffer, 0, waveBuffer, header.length, dataBuffer.length);

        return waveBuffer;
    }

    byte[] ShortToByte(short[] input) {
        int short_index, byte_index;
        int iterations = input.length;
        byte[] buffer = new byte[iterations * 2];

        short_index = byte_index = 0;

        for (/*NOP*/; short_index != iterations; /*NOP*/) {
            buffer[byte_index + 1] = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index] = (byte) ((input[short_index] & 0xFF00) >> 8);

            ++short_index;
            byte_index += 2;
        }

        return buffer;
    }

    public class LimitedQueue<E> extends LinkedList<E> {
        private int limit;

        public LimitedQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean add(E o) {
            super.add(o);
            while (size() > limit) {
                super.remove();
            }
            return true;
        }
    }

    private class TimeValue {
        private long time;
        private byte[] value;

        public TimeValue(long t, byte[] v) {
            time = t;
            value = v;
        }
    }

}
