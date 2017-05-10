/*
Copyright 2017 Erigo Technologies LLC

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

package erigo.ctstream;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.util.Timer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A DataStream for generating webcam images at a periodic rate.
 *
 * Note that the web camera is a static resource for this class.  In this way,
 * the web camera can be left open between instantiations of this
 * WebcamStream class.
 *
 * This class uses the following helper classes to generate the images:
 *
 * ImageTimerTask: The run() method in this class (which is called by a periodic Timer)
 *       creates an instance of ImageTask and executes it in a separate Thread (which
 *       prevents the periodic ImageTimerTask.run() task from bogging down).
 *
 * ImageTask: Generates a webcam image and puts it on WebcamStream's queue.
 *
 * @author John P. Wilson
 * @version 2017-05-09
 */

public class WebcamStream extends DataStream {

    private Timer webcamTimer = null;			    // Periodic Timer object
    private ImageTimerTask webcamTimerTask = null;	// TimerTask executed each time the periodic Timer expires
    public long capturePeriodMillis;                // capture period in milliseconds
    public static Webcam webcam = null;             // webcam object to grab images from

    /**
     * WebcamStream constructor
     *
     * @param ctsI          CTstream object
     * @param channelNameI  Channel name
     */
    public WebcamStream(CTstream ctsI, String channelNameI) {
        super();
        channelName = channelNameI;
        cts = ctsI;
        bCanPreview = true;
    }

    /**
     * Implementation of the abstract start() method from DataStream
     */
    public void start() throws Exception {
        if (webcamTimer != null)		{ throw new Exception("ERROR in WebcamStream.start(): Timer object is not null"); }
        if (webcamTimerTask != null)	{ throw new Exception("ERROR in WebcamStream.start(): ImageTimerTask object is not null"); }
        if (queue != null)				{ throw new Exception("ERROR in WebcamStream.start(): LinkedBlockingQueue object is not null"); }
        bIsRunning = true;
        openWebCamera();
        queue = new LinkedBlockingQueue<TimeValue>();
        // Setup periodic image captures
        startWebcamTimer();
        updatePreview();
    }

    /**
     * Implementation of the abstract stop() method from DataStream
     */
    public void stop() {
        super.stop();
        // shut down the periodic Timer
        stopWebcamTimer();
        // Don't close the web camera here; once it has been opened, leave it open for
        // the duration of the CTstream session (in case user wants to use it again)
        // closeWebCamera();
    }

    /**
     * Implementation of the abstract update() method from DataStream
     *
     * This method is called when there has been some real-time change to the UI settings that may affect this class
     */
    public void update() {
        super.update();
        if (!bIsRunning) {
            // Not currently running; just return
            return;
        }
        // Check the frame rate; if this has changed, start a new Timer
        long updatedCapturePeriodMillis = (long)(1000.0 / cts.framesPerSec);
        if (updatedCapturePeriodMillis != capturePeriodMillis) {
            System.err.println("\nRestarting webcam captures at new rate: " + cts.framesPerSec + " frames/sec");
            startWebcamTimer();
        }
    }

    /**
     * Convenience method to start a new webcamTimer
     *
     * Setup a Timer to periodically call ImageTimerTask.run().  See the
     * notes in the top header for how this fits into the overall program.
     */
    private void startWebcamTimer() {
        // First, make sure any existing webcamTimer is finished
        stopWebcamTimer();
        // Now start the new Timer
        capturePeriodMillis = (long)(1000.0 / cts.framesPerSec);
        webcamTimer = new Timer();
        webcamTimerTask = new ImageTimerTask(cts,this);
        webcamTimer.schedule(webcamTimerTask, 0, capturePeriodMillis);
    }

    /**
     * Convenience method to stop the currently running webcamTimer
     */
    private void stopWebcamTimer() {
        if (webcamTimer != null) {
            webcamTimer.cancel();
            webcamTimer.purge();
            webcamTimer = null;
            webcamTimerTask = null;
        }
    }

    /**
     * Start the web camera
     */
    private static void openWebCamera() {
        if (webcam == null) {
            System.err.println("\nOpen web camera");
            webcam = Webcam.getDefault();
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            // webcam.setDriver(new JmfDriver());
            webcam.open();
        }
    }

    /**
     * Stop the web camera
     */
    public static void closeWebCamera() {
        if (webcam != null) {
            System.err.println("\nClose web camera");
            webcam.close();
            webcam = null;
        }
    }

}
