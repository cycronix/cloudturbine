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

import java.util.concurrent.BlockingQueue;

/**
 * Abstract class to act as the parent of all data stream classes which send data to CloudTurbine.
 *
 * @author John Wilson
 * @version 05/01/2017
 */

public abstract class DataStream {

    public String name = "";

    public BlockingQueue<TimeValue> queue = null;

    public CTstream cts = null;

    // At most 1 DataStream class can specify manual flush;
    // if there is 1 DataStream with manual flush, then all other DataStreams will coordinate flush with that one;
    // if no DataStream has manual flush, then WriteTask will use auto flush (ie, no CTwriter.flush() call will be made)
    public boolean bManualFlush = false;

    public boolean bCanPreview = false;

    public PreviewWindow previewWindow = null;

    // Is this stream currently running?
    public boolean bIsRunning = false;

    /**
     * Start the stream
     */
    public abstract void start();

    /**
     * Stop the stream
     */
    public abstract void stop();

    /**
     * User has changed real-time settings; update the stream to use these new settings
     */
    public abstract void update();
}
