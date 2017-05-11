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
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A DataStream for generating text messages.
 *
 * @author John P. Wilson
 * @version 2017-05-10
 */

public class TextMessagingStream extends DataStream {

    private String lastMsg = "";  // The last message sent to the queue

    /**
     * TextMessagingStream constructor
     *
     * @param ctsI          CTstream object
     * @param channelNameI  Channel name
     */
    // constructor
    public TextMessagingStream(CTstream ctsI, String channelNameI) {
        super();
        channelName = channelNameI;
        cts = ctsI;
        bCanPreview = true;
    }

    /**
     * Implementation of the abstract start() method from DataStream
     */
    public void start() throws Exception {
        if (queue != null) { throw new Exception("ERROR in TextMessagingStream.start(): LinkedBlockingQueue object is not null"); }
        bIsRunning = true;
        queue = new LinkedBlockingQueue<TimeValue>();
        updatePreview();
    }

    /**
     * Implementation of the abstract stop() method from DataStream
     */
    public void stop() {
        super.stop();
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
        // For TextMessagingStream, there are no other real-time settings we need to adjust for; just return
        return;
    }

    /**
     * Update the status of the preview window
     */
    public void updatePreview() {
        super.updatePreview();
        if (bPreview && bIsRunning && cts.bPreview) {
            // set the size of the window
            Dimension previewSize = new Dimension(400,200);
            previewWindow.setFrameSize(previewSize);
            if (!lastMsg.isEmpty()) {
                // update the preview window with the last message we posted on the queue
                previewWindow.updateText(lastMsg);
            }
        }
    }

    /**
     * Post a new text message.
     *
     * @param msgI  The text message to post.
     */
    public void postTextMessage(String msgI) {
        if (!bIsRunning) {
            return;
        }
        if (msgI == null) {
            return;
        }
        String msg = msgI.trim();
        if (msg.isEmpty()) {
            return;
        }
        try {
            queue.put(new TimeValue(cts.getNextTime(), msg.getBytes()));
            lastMsg = msg;
            if (cts.bPrintDataStatusMsg) { System.err.print("t"); }
        } catch (Exception e) {
            if (bIsRunning) {
                System.err.println("\nTextMessagingStream: exception thrown adding data to queue:\n" + e);
                e.printStackTrace();
            }
        }

        if(bPreview && (previewWindow != null)) {
            previewWindow.updateText(msg);
        }

    }

}
