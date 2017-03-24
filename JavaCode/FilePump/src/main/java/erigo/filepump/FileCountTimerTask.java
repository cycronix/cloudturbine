/*
Copyright 2016-2017 Erigo Technologies LLC

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

package erigo.filepump;

/**
 * Simple TimerTask to update the file count label on the FilePump GUI
 * 
 * @author JPW
 *
 */

import java.util.TimerTask;

public class FileCountTimerTask extends TimerTask {
	
	private FilePump pumpGUI = null;
	private FilePumpWorker pumpWorker = null;
	
	public FileCountTimerTask(FilePump pumpGUI_I, FilePumpWorker pumpWorker_I) {
		pumpGUI = pumpGUI_I;
		pumpWorker = pumpWorker_I;
	}
	
	/* (non-Javadoc)
	 * @see java.util.TimerTask#run()
	 */
	@Override
	public void run() {
		if (pumpGUI.bPumpRunning) {
			// Make a local copy of the count (don't know if it matters, but
			// this is a crude attempt at thread safety)
			int index = pumpWorker.file_index;
			pumpGUI.updateNumFiles_nonEDT(index);
		}
	}

}

