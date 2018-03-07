/*
Copyright 2017-2018 Erigo Technologies LLC

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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.TimerTask;

/**
 * ImageTimerTask is a Runnable class whose run() method is called by
 * a periodic Timer to generate an image for a DataStream.  The run()
 * method in turn creates an instance of ImageTask to run in a separate
 * thread so that ImageTimerTask can exit quickly and not miss the next
 * periodic event.
 * 
 * An alternative to using Timer and TimerTask would be to use
 * ScheduledThreadPoolExecutor, which has more powerful capabilities;
 * not really needed now since we only need to schedule 1 task.
 *
 * @author John P. Wilson
 * @version 03/07/2018
 *
 */

public class ImageTimerTask extends TimerTask {
	
	private CTstream cts = null;
	private DataStream dataStream = null;
	
	/**
	 * Constructor
	 * 
	 * @param  ctsI  CTstream object
	 * @param  dataStreamI  DataStream object
	 */
	public ImageTimerTask(CTstream ctsI, DataStream dataStreamI) {
		cts = ctsI;
		dataStream = dataStreamI;
	}
	
	/**
	 * Method called by a periodic Timer; spawn a new thread to generate an image.
	 */
	public void run() {
		if (!dataStream.bIsRunning) {
			return;
		}
		if (dataStream instanceof ScreencapStream) {
			if (((ScreencapStreamSpec)dataStream.spec).bFullScreen) {
				((ScreencapStream)dataStream).regionToCapture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			} else {
				// User will specify the region to capture via the JFame
				// If the JFrame is not yet up, just return
				if ((cts.guiFrame == null) || (!cts.guiFrame.isShowing())) {
					return;
				}
				// Update capture region (this is used by ImageTask)
				// Trim off a couple pixels from the edges to avoid getting
				// part of the red frame in the screen capture.
				Point loc = cts.capturePanel.getLocationOnScreen();
				loc.x = loc.x + 2;
				loc.y = loc.y + 2;
				Dimension dim = cts.capturePanel.getSize();
				if ((dim.width <= 0) || (dim.height <= 0)) {
					// Don't try to do a screen capture with a non-existent capture area
					return;
				}
				dim.width = dim.width - 4;
				dim.height = dim.height - 4;
				((ScreencapStream)dataStream).regionToCapture = new Rectangle(loc, dim);
			}
		}
		// Run an instance of ImageTask in a new thread
        Thread threadObj = new Thread(new ImageTask(cts,dataStream,(ImageStreamSpec)dataStream.spec));
        threadObj.start();
	}
	
}
