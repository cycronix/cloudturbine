/*
Copyright 2018 Erigo Technologies LLC

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

/**
 * Specifications pertaining to image streams.
 *
 * @author John P. Wilson
 * @version 2018-03-06
 */

public class ImageStreamSpec extends DataStreamSpec {

    public double framesPerSec = 5.0;      // How many frames to capture per second

    public float imageQuality = 0.70f;     // Image quality; 0.00 - 1.00; higher numbers correlate to better quality/less compression

    public boolean bChangeDetect = false;  // Detect and record only images that change (more CPU, less storage)

}
