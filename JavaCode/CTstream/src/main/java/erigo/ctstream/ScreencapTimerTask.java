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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.TimerTask;

/**
 * ScreencapTimerTask is a Runnable class called by the periodic Timer
 * to take a screen capture.  The work of taking the screen capture is
 * handled in a separate Thread so that ScreencapTimerTask.run() can
 * exit very quickly and not hold up the periodic Timer.
 * 
 * An alternative to using Timer and TimerTask would be to use
 * ScheduledThreadPoolExecutor, which has more powerful capabilities;
 * not really needed now since we only need to schedule 1 task.
 *
 * @author John P. Wilson
 * @version 02/07/2017
 *
 */

public class ScreencapTimerTask extends TimerTask {
	
	public CTstream cts = null;
	
	/**
	 * 
	 * Constructor
	 * 
	 * @param  ctsI  The CTstream object.
	 */
	public ScreencapTimerTask(CTstream ctsI) {
		cts = ctsI;
	}
	
	/**
	 * Method called by the periodic timer (CTstream.screencapTimer);
	 * spawn a new thread to perform the screen capture.
	 *
	 * Possibly using a thread pool would save some thread management overhead?
	 */
	public void run() {
		if (cts.bShutdown) {
			return;
		}
		if (!cts.bFullScreen  && !cts.bWebCam) {
			// User will specify the region to capture via the JFame
			// If the JFrame is not yet up, just return
			if ( (cts.guiFrame == null) || (!cts.guiFrame.isShowing()) ) {
				return;
			}
			// Update capture region (this is used by ScreencapTask)
			// Trim off a couple pixels from the edges to avoid getting
			// part of the red frame in the screen capture.
			Point loc = cts.capturePanel.getLocationOnScreen();
			loc.x = loc.x + 2;
			loc.y = loc.y + 2;
			Dimension dim = cts.capturePanel.getSize();
			if ( (dim.width <= 0) || (dim.height <= 0) ) {
				// Don't try to do a screen capture with a non-existent capture area
				return;
			}
			dim.width = dim.width - 4;
			dim.height = dim.height - 4;
			Rectangle tempRegionToCapture = new Rectangle(loc,dim);
			cts.regionToCapture = tempRegionToCapture;
		}
		// Create a new ScreencapTask and run it in a new thread
		ScreencapTask screencapTask = new ScreencapTask(cts);
        Thread threadObj = new Thread(screencapTask);
        threadObj.start();
	}
	
}
