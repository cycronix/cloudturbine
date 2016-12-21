/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package erigo.cttext;

/**
 * 
 * Handle events when the user changes text in the JTextArea.
 * 
 * CTwriter will flush data every time the user hits ENTER in the text area.
 * We use a Key Binding and the actionPerformed() method in this class to
 * be notified when the user has hit ENTER.  When this occurs, the
 * Key Binding action (ie, method actionPerformed()) will set a flag
 * (ie, bTimeToFlush) to signal DocumentListener that a flush needs to take place.
 * 
 * Example/forum posts about DocumentListener, Key Bindings and Key Listeners:
 * 1. DocumentListener and Key Binding: http://docs.oracle.com/javase/tutorial/uiswing/components/generaltext.html
 * 2. Key Binding (good tutorial): http://www.dreamincode.net/forums/topic/245148-java-key-binding-tutorial-and-demo-program/
 * 3. Key Binding versus Key Listener: http://stackoverflow.com/questions/15290035/key-bindings-vs-key-listeners-in-java
 * 
 */

import cycronix.ctlib.*;

import java.io.IOException;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

public class DocumentChangeListener extends AbstractAction implements DocumentListener {
    
	private static final long serialVersionUID = 2349959704230725018L;
	private CTtext hCTtext = null;
	private CTwriter ctw = null;
	private boolean bTimeToFlush = false;
	
	/*
	 * Constructor
	 * 
	 * Save CTtext object handle. 
	 */
	public DocumentChangeListener(CTtext cttextI) {
		hCTtext = cttextI;
		// CTinfo.setDebug(true);
	}
	
	// Key binding callback when the user hits ENTER in the JTextArea;
	// signal the DocumentListener that it is time to flush by setting
	// bTimeToFlush to true
	//
	// NOTE: The key binding is currently disabled in CTtext.createAndShowGUI()  
	//
	public void actionPerformed( ActionEvent ae ) {
		// It is time to do a flush
		bTimeToFlush = true;
		
		// Append the line feed in the JTextArea
		// This will result in the DocumentListener being called
		JTextArea textArea = (JTextArea)ae.getSource();
		textArea.append(System.getProperty("line.separator"));
	}
	
	/* (non-Javadoc)
	 * @see javax.swing.event.DocumentListener#changedUpdate(javax.swing.event.DocumentEvent)
	 */
	@Override
	public void changedUpdate(DocumentEvent e) {
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
	}

	/* (non-Javadoc)
	 * @see javax.swing.event.DocumentListener#insertUpdate(javax.swing.event.DocumentEvent)
	 */
	@Override
	public void insertUpdate(DocumentEvent e) {
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
	}

	/* (non-Javadoc)
	 * @see javax.swing.event.DocumentListener#removeUpdate(javax.swing.event.DocumentEvent)
	 */
	@Override
	public void removeUpdate(DocumentEvent e) {
		if (!hCTtext.ctSettings.getBManualFlush()) {
			processText(e);
		}
	}
	
	/*
	 * If CloudTurbine streaming is turned on and the document length is greater than 0,
	 * then send text to CloudTurbine.
	 * 
	 * Question: If a user is typing fast enough or the document is large enough, will
	 *           we be able to keep up with the changes?  If not, we could do the
	 *           following:
	 *           a) Have all CTwriter operations running in a separate thread
	 *           b) Every time a Document event comes in, grab the text and add it
	 *              as a new Element in a Vector
	 *           c) As fast as the CTwriter thread is able, it will grab Elements
	 *              off the Vector and write to CloudTurbine.
	 * 
	 * NOTE: Be careful how we use the variable DocumentEvent e_use_carefully, as
	 *       there are cases where direct calls to processText() are made with
	 *       a null argument.  For instance, when the user manually "Saves"
	 *       the document, we call this method directly from CTtext.actionPerformed()
	 * 
	 */
    public void processText(DocumentEvent e_use_carefully) {
    	
    	// Flush if bTimeToFlush is true (which is set in actionPerformed())
    	boolean bLocalTimeToFlush = bTimeToFlush;
    	bTimeToFlush = false;
    	
    	// Only process data if CloudTurbine streaming is active
    	if (!hCTtext.bCTrunning) {
    		if (ctw != null) {
    			closeStreaming();
    		}
    		return;
    	}
    	
    	CTsettings ctSettings = hCTtext.ctSettings;
    	
    	// Create the CTwriter object if needed
    	if (ctw == null) {
    		String destFolder = ctSettings.getOutputFolder();
    		System.err.println("Create CTwriter, output to \"" + destFolder + "\"");
    		String errMsg = null;
    		if (!ctSettings.getBUseFTP()) {
    			try {
    				ctw = new CTwriter(destFolder);
    			} catch (IOException ioe) {
    				errMsg = "Error trying to create CloudTurbine writer object:\n" + ioe.getMessage();
    			}
    		} else {
    			CTftp ctftp = null;
    			try {
    				ctftp = new CTftp(destFolder);
    			} catch (IOException ioe) {
    				errMsg = "Error trying to create CloudTurbine FTP writer object:\n" + ioe.getMessage();
    			}
    			try {
    				ctftp.login(ctSettings.getFTPHost(),ctSettings.getFTPUser(),ctSettings.getFTPPassword());
    				// upcast to CTWriter
        			ctw = ctftp;
    			} catch (Exception e) {
    				errMsg = "Error logging into FTP server \"" + ctSettings.getFTPHost() + "\":\n" + e.getMessage();
    			}
    		}
    		if (errMsg != null) {
    			closeStreaming();
    			hCTtext.enableEditingUI(false,false);
    			System.err.println(errMsg);
    			try {
    			    JOptionPane.showMessageDialog(hCTtext.getParentFrame(), errMsg, "CloudTurbine error", JOptionPane.ERROR_MESSAGE);
    			} catch (HeadlessException he) {
    				// Nothing to do
    			}
    			return;
    		}
    		ctw.autoSegment(ctSettings.getBlocksPerSegment());
    		// "Block" mode is for packed data (multiple points per output file);
    		// we don't want this; in our case, each output file will be one "data point"
    		ctw.setBlockMode(false);
    		ctw.setTimeRelative(ctSettings.getBUseRelativeTime());
    		ctw.setZipMode(ctSettings.getBZipData(),1);
    		if (!ctSettings.getBManualFlush()) {
    			if ((int)ctSettings.getFlushInterval() == -1) {
    				// This is "max responsive" mode; there's no ZIP, so data is written out right away;
    				// the length of a Block (ie, flush interval) is specified by the constant maxResponsivenessFlushInterval
    				ctw.autoFlush(ctSettings.getMaxResponsivenessFlushInterval(),true);
    			} else {
    				ctw.autoFlush(ctSettings.getFlushInterval(),true);
    			}
    		}
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
    		} catch (BadLocationException e1) {
    			System.err.println("Error fetching text:\n" + e1);
    			return;
    		}
    		try {
    			ctw.setTime(System.currentTimeMillis());
    			ctw.putData(ctSettings.getChanName(), currentText);
    			// Flush if either of the following is true:
    			// 1. bLocalTimeToFlush is true; we don't currently use this; this variable will be true
    			//    if we are responding to ENTER key presses by the user in the text area; this method
    			//    isn't currently being used
    			// 2. if we *aren't* doing auto flushes (which are handled asynchronously) then go ahead and flush
    			if ( (bLocalTimeToFlush) || (ctSettings.getBManualFlush()) ) {
    				ctw.flush();
    			}
    		} catch (Exception e1) {
    			System.err.println("\nError writing data to CloudTurbine:\n" + e1.getMessage());
    			return;
    		}
    	}
    	
    }
    
    /*
     * Cleanly shut down streaming.
     */
    public void closeStreaming() {
    	if (ctw != null) {
    	    ctw.close();
    	}
		ctw = null;
    }
    
}
