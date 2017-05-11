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

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

/**
 *
 * Handle events when the user changes text in the JTextArea.
 *
 */

public class DocumentChangeListener implements DocumentListener {

    private CTstream cts = null;

    /**
     * Constructor
     *
     * Save CTstream object handle.
     *
     * @param ctStreamI CTstream instance
     */
    public DocumentChangeListener(CTstream ctStreamI) {
        cts = ctStreamI;
    }

    /**
     * changedUpdate
     *
     * @see javax.swing.event.DocumentListener#changedUpdate(javax.swing.event.DocumentEvent)
     *
     * @param e The DocumentEvent that has occurred.
     */
    @Override
    public void changedUpdate(DocumentEvent e) {
        processText(e);
    }

    /**
     * insertUpdate
     *
     * @see javax.swing.event.DocumentListener#insertUpdate(javax.swing.event.DocumentEvent)
     *
     * @param e The DocumentEvent that has occurred.
     */
    @Override
    public void insertUpdate(DocumentEvent e) {
        processText(e);
    }

    /**
     * removeUpdate
     *
     * @see javax.swing.event.DocumentListener#removeUpdate(javax.swing.event.DocumentEvent)
     *
     * @param e The DocumentEvent that has occurred.
     */
    @Override
    public void removeUpdate(DocumentEvent e) {
        processText(e);
    }

    /**
     * processText
     *
     * If text streaming is turned on and the document length is greater than 0,
     * then queue a message to be sent to CloudTurbine.
     *
     * @param e  The DocumentEvent that has occurred.
     */
    public void processText(DocumentEvent e) {

        // Only process data if CloudTurbine streaming is active
        if ( (cts.textStream == null) || (!cts.textStream.bIsRunning) ) {
            return;
        }

        // Write out document (as long as it isn't empty)
        Document document = cts.textTextArea.getDocument();
        int doc_len = document.getLength();
        String updatedText = " ";  // if no text is in the document, leave a space as a placeholder
        try {
            if (doc_len > 0) {
                updatedText = document.getText(0, document.getLength());
            }
            cts.textStream.postText(updatedText);
        } catch (Exception excep) {
            // Only print out error message if the stream isn't shut down
            if ( (cts.textStream != null) && (cts.textStream.bIsRunning) ) {
                System.err.println("\nError processing text:\n" + excep);
            }
        }

    }

}
