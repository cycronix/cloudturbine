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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Timer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A DataStream for generating screen capture images at a periodic rate.
 *
 * This class uses the following helper classes to generate screen captures:
 *
 * ImageTimerTask: The run() method in this class (which is called by a periodic Timer)
 *       determines the region of the screen to capture, stores this in a variable in
 *       ScreencapStream, creates an instance of ImageTask and executes it in a
 *       separate Thread (which prevents the periodic ImageTimerTask.run() task from
 *       bogging down).
 *
 * ImageTask: Generates a screen capture and puts it on ScreencapStream's queue.
 *
 * @author John P. Wilson
 * @version 2017-05-02
 */

public class ScreencapStream extends DataStream {

    // Encoded byte array of the cursor image data from "Cursor_NoShadow.png"
    private static String encoded_cursor_noshadow = "iVBORw0KGgoAAAANSUhEUgAAABQAAAAgCAYAAAASYli2AAACa0lEQVR42q2WT2jSYRjHR+QtwX/oREVBhIYxGzJNyEOIXTp0yr8HYTRQlNz8O6dgoJ4FT2ZeJAw8CEkdhBYMFHfypLBDsZ3CUYcijErn7+l9BCPZbJrvF97L+/D7/N7neZ8/74rVan27QlngdDpfUQWKxWJwuVwvqQG73S6srq7C1tbWMyrAwWAAnU4HhEIhbG9vZ6kAUe12GwQCAbjd7jQVIOro6Ah4PB7j9XpjVICow8ND4HA4jM/ne0IFiKrX68DlcpmdnZ3HVICoWq02dn93d9dBBYiqVCrA5/NHoVDoIRUgqlQqYUoh9D4VICqfz2NFnUej0btUgKhsNgsymWy4t7e3SQWIymQyoFAofsVisVtUgKh4PA4qlepHIpFQUQEyDAOBQADW1ta+E7hsaeAESmoe1tfXvxH3RUsDUaPRCPsoaLXaL8lkkrcQ8OTkBFqt1oXVaDRAo9GAXq//TKA35gaSmgY2m81g3GYti8Xybm7g8fHxuK7JKTj/ldgHBwdweno6tWcymXBMPF8YWK1WgcVigd/vv7CvVqv7CwHL5TJ2F4bMlhzph9Dv9//YhsMhSKVSjKdrLmCxWMSZMiJJ+wgNBoPhU6FQmDplKpUCs9n8/kpgLpcDkUh0HgwGH0wMHo/nKaYEJvFEvV4PbxvLTzkTmE6nQSKRDMPh8L2/DeRGr2N3aTabU6e02Wxgt9tfzwTK5fJBJBK5c5mRfPjG4XBMxZEML1AqlT8vpaFhf3//9qy/YUdBF8/OzsZze2NjA9fXmd2bFPbNq9KAXMIHnU43noKkdl+QUFxb6mmBaWI0Gj/+y5OJfgOMmgOC3DbusQAAAABJRU5ErkJggg==";

    public BufferedImage cursor_img = null;                 // cursor to add to the screen captures
    private Timer screencapTimer = null;			        // Periodic Timer object
    private ImageTimerTask screencapTimerTask = null;	// TimerTask executed each time the periodic Timer expires
    public long capturePeriodMillis;                        // capture period in milliseconds
    public Rectangle regionToCapture = null;                // Region of the screen to capture

    /**
     * ScreencapStream constructor
     *
     * @param ctsI          CTstream object
     * @param channelNameI  Channel name
     */
    public ScreencapStream(CTstream ctsI, String channelNameI) {
        channelName = channelNameI;
        cts = ctsI;
        bCanPreview = true;
        // Decode the String corresponding to binary cursor data; produce a BufferedImage with it
        Base64.Decoder byte_decoder = Base64.getDecoder();
        byte[] decoded_cursor_bytes = byte_decoder.decode(encoded_cursor_noshadow);
        try {
            cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
        } catch (IOException ioe) {
            System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
            return;
        }
    }

    /**
     * Implementation of the abstract start() method from DataStream
     */
    public void start() throws Exception {
        if (screencapTimer != null)		{ throw new Exception("ERROR in ScreencapStream.start(): Timer object is not null"); }
        if (screencapTimerTask != null)	{ throw new Exception("ERROR in ScreencapStream.start(): ImageTimerTask object is not null"); }
        if (queue != null)				{ throw new Exception("ERROR in ScreencapStream.start(): LinkedBlockingQueue object is not null"); }
        bIsRunning = true;
        queue = new LinkedBlockingQueue<TimeValue>();
        // Setup periodic image captures (either from web camera or screen)
        startScreencapTimer();
        updatePreview();
    }

    /**
     * Implementation of the abstract stop() method from DataStream
     */
    public void stop() {
        super.stop();
        // shut down the periodic Timer
        stopScreencapTimer();
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
            System.err.println("\nRestarting screen captures at new rate: " + cts.framesPerSec + " frames/sec");
            startScreencapTimer();
        }
    }

    /**
     * Convenience method to start a new screencapTimer
     *
     * Setup a Timer to periodically call ImageTimerTask.run().  See the
     * notes in the top header for how this fits into the overall program.
     */
    private void startScreencapTimer() {
        // First, make sure any existing screencapTimer is finished
        stopScreencapTimer();
        // Now start the new Timer
        capturePeriodMillis = (long)(1000.0 / cts.framesPerSec);
        screencapTimer = new Timer();
        screencapTimerTask = new ImageTimerTask(cts,this);
        screencapTimer.schedule(screencapTimerTask, 0, capturePeriodMillis);
    }

    /**
     * Convenience method to stop the currently running screencapTimer
     */
    private void stopScreencapTimer() {
        if (screencapTimer != null) {
            screencapTimer.cancel();
            screencapTimer.purge();
            screencapTimer = null;
            screencapTimerTask = null;
        }
    }

    /**
     * Utility function which reads the specified image file and returns
     * a String corresponding to the encoded bytes of this image.
     *
     * Sample code for calling this method to generate an encoded string representation of a cursor image:
     * String cursorStr = null;
     * try {
     *     cursorStr = getEncodedImageString("cursor.png","png");
     *     System.err.println("Cursor string:\n" + cursorStr + "\n");
     * } catch (IOException ioe) {
     *     System.err.println("Caught exception generating encoded string for cursor data:\n" + ioe);
     * }
     *
     * A buffered image can be created from this encoded String as follows:
     * Base64.Decoder byte_decoder = Base64.getDecoder();
     * byte[] decoded_cursor_bytes = byte_decoder.decode(cursorStr);
     * try {
     *     BufferedImage cursor_img = ImageIO.read(new ByteArrayInputStream(decoded_cursor_bytes));
     * } catch (IOException ioe) {
     *     System.err.println("Error creating BufferedImage from cursor data:\n" + ioe);
     * }
     *
     * ImageTask includes code to draw this BufferedImage into the screencapture image
     * using Graphics2D (the reason we do this is because the screencapture doesn't include
     * the cursor).
     *
     * @return A String corresponding to the encoded bytes of the specified image.
     *
     * @param fileNameI		Name of the image file
     * @param formatNameI	Format of the image file ("png", for instance)
     *
     * @throws IOException if there are problems reading or writing the image or if there is an error flushing the ByteArrayOutputStream
     */
    public static String getEncodedImageString(String fileNameI, String formatNameI) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
        BufferedImage img = ImageIO.read(new File(fileNameI));
        boolean bFoundWriter = ImageIO.write(img, formatNameI, baos);
        if (!bFoundWriter) {
            throw new IOException( new String("No image writer found for format \"" + formatNameI + "\"") );
        }
        baos.flush();
        byte[] cursor_bytes = baos.toByteArray();
        Base64.Encoder byte_encoder = Base64.getEncoder();
        return byte_encoder.encodeToString(cursor_bytes);
    }

}
