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

package erigo.cttext;

/**
 * 
 * Handle events when the user changes text in the JTextArea.
 * 
 */

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

public class DocumentChangeListener implements DocumentListener {
	
	private CTtext hCTtext = null;
	
	/**
	 * Constructor
	 * 
	 * Save CTtext object handle. 
	 * 
	 * @param cttextI CTtext instance
	 */
	public DocumentChangeListener(CTtext cttextI) {
		hCTtext = cttextI;
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
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
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
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
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
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
	}
	
	/**
	 * processText
	 * 
	 * If CloudTurbine streaming is turned on and the document length is greater than 0,
	 * then queue a message to be sent to CloudTurbine.
	 * 
	 * NOTE: Be careful how we use the variable DocumentEvent e_use_carefully, as
	 *       there are cases where direct calls to processText() are made with
	 *       a null argument.  For instance, when the user manually "Saves"
	 *       the document, we call this method directly from CTtext.actionPerformed()
	 * 
	 * @param e_use_carefully  The DocumentEvent that has occurred; when this method is called manually, this parameter will be null.
	 */
    public void processText(DocumentEvent e_use_carefully) {
    	
    	// Only process data if CloudTurbine streaming is active
    	if (!hCTtext.bCTrunning) {
    		return;
    	}
    	
    	// Write out document (as long as it isn't empty)
    	Document document = null;
    	if (e_use_carefully != null) {
    	    document = (Document) e_use_carefully.getDocument();
    	} else {
    	    document = hCTtext.getTextArea().getDocument();
    	}
    	int doc_len = document.getLength();
    	if (doc_len > 0) {
    		String currentText = null;
    		try {
    			currentText = document.getText(0, document.getLength());
    			// System.err.println("\n<<\n" + currentText + "\n>>\n");
    			System.out.print(".");
    			// Add currentText to the queue
        		hCTtext.queue.put(currentText);
    		} catch (Exception excep) {
    			if (hCTtext.bCTrunning) {
    				// Only print out error messages if we know we aren't in shutdown mode
    			    System.err.println("\nError processing text:\n" + excep);
    			    excep.printStackTrace();
    			}
    			return;
    		}
    	}
    	
    }
    
}
