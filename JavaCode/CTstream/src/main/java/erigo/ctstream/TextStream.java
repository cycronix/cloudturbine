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

import javax.swing.text.Document;
import java.awt.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A DataStream for generating text.
 *
 * @author John P. Wilson
 * @version 2017-05-11
 */

public class TextStream extends DataStream {

    private String lastMsg = "";  // The last message sent to the queue

    /**
     * TextStream constructor
     *
     * @param ctsI          CTstream object
     * @param channelNameI  Channel name
     */
    // constructor
    public TextStream(CTstream ctsI, String channelNameI) {
        super(PreviewWindow.PreviewType.TEXT);
        channelName = channelNameI;
        cts = ctsI;
        bCanPreview = true;
    }

    /**
     * Implementation of the abstract start() method from DataStream
     */
    public void start() throws Exception {
        if (queue != null) { throw new Exception("ERROR in TextStream.start(): LinkedBlockingQueue object is not null"); }
        bIsRunning = true;
        queue = new LinkedBlockingQueue<TimeValue>();
        updatePreview();
        // If user has already added text, post it right away
        Document document = cts.textArea.getDocument();
        if (document.getLength() > 0) {
            postText(document.getText(0, document.getLength()));
        }
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
        // For TextStream, there are no other real-time settings we need to adjust for; just return
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
     * Post updated text.
     *
     * @param textI  The text message to post.
     */
    public void postText(String textI) {
        if (!bIsRunning) {
            return;
        }
        if (textI == null) {
            return;
        }
        if (textI.isEmpty()) {
            return;
        }
        try {
            queue.put(new TimeValue(cts.getNextTime(), textI.getBytes()));
            lastMsg = textI;
            if (cts.bPrintDataStatusMsg) { System.err.print("t"); }
        } catch (Exception e) {
            if (bIsRunning) {
                System.err.println("\nTextStream: exception thrown adding data to queue:\n" + e);
                e.printStackTrace();
            }
        }

        if(bPreview && (previewWindow != null)) {
            previewWindow.updateText(textI);
        }

    }

}
