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

    private static final HashMap<RenderingHints.Key, Object> renderingProperties = new HashMap<>();

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
        // For creating an image preview of the text string
        renderingProperties.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        renderingProperties.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        renderingProperties.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
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
            System.out.print("t");
        } catch (Exception e) {
            if (bIsRunning) {
                System.err.println("\nTextMessagingStream: exception thrown adding data to queue:\n" + e);
                e.printStackTrace();
            }
        }

        if(bPreview && (previewWindow != null)) {
            // BufferedImage img = textToImage(msg, Font f, float Size);
            // previewWindow.updateImage(img,img.getWidth(),img.getHeight());
            previewWindow.updateText(msg);
        }

    }

    /**
     * Convert String to image.
     *
     * This example was posted by "initramfs" at http://stackoverflow.com/questions/18800717/convert-text-content-to-image
     *
     * @param Text
     * @param f
     * @param Size
     * @return
     */
    private static BufferedImage textToImage(String Text, Font f, float Size) {
        //Derives font to new specified size, can be removed if not necessary.
        f = f.deriveFont(Size);

        FontRenderContext frc = new FontRenderContext(null, true, true);

        //Calculate size of buffered image.
        LineMetrics lm = f.getLineMetrics(Text, frc);

        Rectangle2D r2d = f.getStringBounds(Text, frc);

        BufferedImage img = new BufferedImage((int)Math.ceil(r2d.getWidth()), (int)Math.ceil(r2d.getHeight()), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHints(renderingProperties);

        g2d.setBackground(Color.WHITE);
        g2d.setColor(Color.BLACK);

        g2d.clearRect(0, 0, img.getWidth(), img.getHeight());

        g2d.setFont(f);

        g2d.drawString(Text, 0, lm.getAscent());

        g2d.dispose();

        return img;
    }

}
