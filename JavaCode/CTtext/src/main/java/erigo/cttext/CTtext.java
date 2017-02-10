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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import cycronix.ctlib.*;

/**
 * 
 * Save user-entered text from a JText UI control to CloudTurbine.
 * 
 * Classes involved in this application:
 * -------------------------------------
 * 1. CTtext: main class; manages the GUI and program flow; manages the CTwriter object
 * 2. DocumentChangeListener: responds to text edit events; save a "snapshot" of
 *       the text as a String to the blocking queue.
 * 3. WriteTask: grab Strings off the queue and write them to CT; this is executed
 *       in a separate Thread
 * 4. CTsettings: saves settings the user has specified about how to save data to CT;
 *       also supports a dialog to allow the user to edit these settings
 * 5. Utility: contains a utility method to add a control to the UI
 * 
 * Two flushing modes are supported: user can manually 
 *
 * @author John P. Wilson
 * @version 02/10/2017
 *
 */
public class CTtext implements ActionListener {

	// GUI components
	private JFrame cttextGuiFrame = null;
	private JTextArea textArea = null;
	private JScrollPane scrollPane = null;
	private JButton manualFlushButton = null;
	
	// Queue of Strings waiting to be written to CT
	public BlockingQueue<String> queue = null;
	
	// Objects which do "work"
	public CTwriter ctw = null;						// CTwriter instance, used by WriteTask
	private DocumentChangeListener ctDoc = null;	// Responds to user edits by saving entire document as a String and putting it in the queue
	private WriteTask writeTask = null;				// This task takes Strings off the queue and writes them to CT
	private Thread writeTaskThread = null;			// The thread running in WriteTask
	
	// Lock object used by the synchronized blocks in CTtext and WriteTask
	public Object ctwLockObj = new Object();
	
	// CloudTurbine settings
	public CTsettings ctSettings = null;
	public boolean bDebugMode = false;	// run CT in debug mode?
	public boolean bCTrunning = false;	// is CTwriter open?
	public boolean bForceFlush = false;	// used with manual flush; force flush on CTwriter
	
    /**
     * main
     * 
     * Create an instance of CTtext and pop up its GUI.
     * 
     * @param argsI Input arguments (none currently supported).
     */
	public static void main(String[] argsI) {
        
		final CTtext cttext = new CTtext();
        
		// For thread safety: Schedule a job for the event-dispatching thread
		// to create and show this application's GUI.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				cttext.createAndShowGUI();
			}
		});
        
	}
    
	/**
	 * CTtext constructor
	 */
	public CTtext() {
		ctDoc = new DocumentChangeListener(this);
	}
    
	/**
	 * createAndShowGUI
	 * 
	 * Create the CTtext GUI; this method should be run on the event dispatch thread.
	 */
	private void createAndShowGUI() {
		
		// Make sure we have nice window decorations.
		JFrame.setDefaultLookAndFeelDecorated(true);
		
		// Create the GUI components
		GridBagLayout framegbl = new GridBagLayout();
		cttextGuiFrame = new JFrame("CTtext");
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		textArea = new JTextArea(15,85);
		// Add a Document listener to the JTextArea; this is how we
		// will listen for changes to the document
		textArea.getDocument().addDocumentListener(ctDoc);
		// The text area isn't enabled until user clicks "Start" button to start streaming
		textArea.setEditable(false);
		textArea.setBackground(Color.LIGHT_GRAY);
		
		//
		// DISABLE THE KEY BINDING SINCE WE DON'T NEED TO RESPOND TO LINE FEEDS;
		// CTwriter WILL EITHER FLUSH CONTENT WITH EVERY EDIT OR ELSE USE ASYNC AUTO FLUSH
		//
		// Add a KeyBinding to the JTextArea so we know when the user hits the ENTER key
		// Information about Key Bindings and Key Listeners:
		// o Key Binding (good tutorial): http://www.dreamincode.net/forums/topic/245148-java-key-binding-tutorial-and-demo-program/
		// o Key Binding versus Key Listener: http://stackoverflow.com/questions/15290035/key-bindings-vs-key-listeners-in-java
		// o DocumentListener and Key Binding: http://docs.oracle.com/javase/tutorial/uiswing/components/generaltext.html
		// textArea.getInputMap().put(KeyStroke.getKeyStroke( "ENTER" ),"doEnterAction" );
		// Specify that the action "doEnterAction" will be implemented by this object
		// (the actionPerformed() method will get a callback when the user hits ENTER)
		// textArea.getActionMap().put("doEnterAction",this);
		
		scrollPane = new JScrollPane(textArea);
		
		// Create a small button which will be active if user has selected "Manual flush on button click"
		manualFlushButton = new JButton("Flush");
		manualFlushButton.setFont(new Font("Dialog", Font.BOLD, 10));
		manualFlushButton.setBorder(null);
		manualFlushButton.setBorderPainted(false);
		manualFlushButton.setMargin(new Insets(2,2,2,2));
		manualFlushButton.addActionListener(this);
		manualFlushButton.setEnabled(false);
		
		cttextGuiFrame.setFont(new Font("Dialog", Font.PLAIN, 12));
		guiPanel.setFont(new Font("Dialog", Font.PLAIN, 12));

		int row = 0;

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;

		// First row: the text area inside of a scroll pane
		gbc.insets = new Insets(15, 15, 0, 15);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		Utility.add(guiPanel, scrollPane, gbl, gbc, 0, row, 1, 1);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		++row;

		// Second row:
		// Manual Flush button, only activated if user has selected
		// "Manual flush on button click" in the Settings window
		gbc.insets = new Insets(0, 0, 5, 15);
		gbc.anchor = GridBagConstraints.NORTHEAST;
		Utility.add(guiPanel, manualFlushButton, gbl, gbc, 0, row, 1, 1);
		gbc.anchor = GridBagConstraints.WEST;
		++row;

		// Now add guiPanel to cttextGuiFrame
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0, 0, 0, 0);
		Utility.add(cttextGuiFrame, guiPanel, framegbl, gbc, 0, 0, 1, 1);
		
		// Add menu
		JMenuBar menuBar = createMenu();
		cttextGuiFrame.setJMenuBar(menuBar);
		
		// Display the window.
		cttextGuiFrame.pack();

		cttextGuiFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		cttextGuiFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit();
			}
		});
		
		ctSettings = new CTsettings(cttextGuiFrame);
		
		cttextGuiFrame.setVisible(true);
		
	}
	
	/**
	 * Create the menu to be added to the GUI
	 * 
	 * @return the menu to be added to the GUI
	 */
	private JMenuBar createMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu menu = new JMenu("File");
		menuBar.add(menu);
		JMenuItem menuItem = new JMenuItem("Settings...");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		menuItem = new JMenuItem("Exit");
		menu.add(menuItem);
		menuItem.addActionListener(this);
		return menuBar;
	}
	
	/**
	 * 
	 * @return the parent GUI frame
	 */
	public JFrame getParentFrame() {
		return cttextGuiFrame;
	}
	
	/**
	 * 
	 * @return the text area UI control
	 */
	public JTextArea getTextArea() {
		return textArea;
	}
	
	/**
	 * Action callback method.
	 * 
	 * @param eventI The event that has occurred.
	 */
	public void actionPerformed(ActionEvent eventI) {

		Object source = eventI.getSource();

		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Settings...")) {
			// Turn off streaming
			stopTextToCT();
			// Disable editing
			enableEditingUI(false,false);
			// Let user edit settings
			ctSettings.popupSettingsDialog();
			// Turn streaming on if:
			// 1. user clicked the OK button on the settings dialog
			// 2. the user-entered settings are acceptable
			if ( (ctSettings.getBClickedOK()) && (ctSettings.canCTrun().isEmpty()) ) {
				bCTrunning = true;
				// Enable editing
				enableEditingUI(true,ctSettings.getBManualFlush());
			}
		} else if (eventI.getActionCommand().equals("Flush")) {
			// This will only occur if manual flush is turned on
			// Manually save the document;
			// in WriteTask, ctw.flush() will be called since manual flush is turned on
			ctDoc.processText(null);
		} else if (eventI.getActionCommand().equals("Exit")) {
			exit();
		}
		
	}
	
	/**
	 * Setup the user interface to either enable or disable editing
	 * 
	 * @param bEnableTextArea Should the text area be enabled?
	 * @param bEnableManualFlush Should the manual flush button be enabled?
	 */
	public void enableEditingUI(boolean bEnableTextArea,boolean bEnableManualFlush) {
		textArea.setEditable(bEnableTextArea);
		manualFlushButton.setEnabled(bEnableManualFlush);
		if (bEnableTextArea) {
			textArea.setBackground(Color.WHITE);
		} else {
		    textArea.setBackground(Color.LIGHT_GRAY);
		}
	}
	
	/**
	 * startTextToCT
	 * 
	 * Open CTwriter and supporting resources to allow text to stream to CloudTurbine.
	 */
	public void startTextToCT() {
		
		// Firewalls
		// By the time we get to this function, ctw should be null (ie, no currently active CT connection)
		if (ctw != null) 		{ System.err.println("ERROR in startTextToCT(): CTwriter object is not null; returning"); return; }
		if (writeTask != null)	{ System.err.println("ERROR in startTextToCT(): WriteTask object is not null; returning"); return; }
		if (queue != null)		{ System.err.println("ERROR in startTextToCT(): LinkedBlockingQueue object is not null; returning"); return; }
		
		System.err.println("\nStart new periodic screen capture");
		
		bCTrunning = true;
		
		//
		// Setup CTwriter
		//
		String destFolder = ctSettings.getOutputFolder();
		System.err.println("Create CTwriter, output to \"" + destFolder + "\"");
		String errMsg = null;
		CTinfo.setDebug(bDebugMode);
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
			stopTextToCT();
			enableEditingUI(false,false);
			System.err.println(errMsg);
			try {
				JOptionPane.showMessageDialog(getParentFrame(), errMsg, "CloudTurbine error", JOptionPane.ERROR_MESSAGE);
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
		
		//
		// Setup the asynchronous event queue to store Strings
		//
		queue = new LinkedBlockingQueue<String>();
		
		//
		// Create a new WriteTask which continually grabs Strings off the queue and writes them to CT
		// Run this in a new thread
		//
		writeTask = new WriteTask(this);
		writeTaskThread = new Thread(writeTask);
		writeTaskThread.start();
		
	}
	
	/**
     * stopTextToCT
     * 
     * Cleanly shut down text streaming to CloudTurbine.
     */
    public void stopTextToCT() {
    	
    	System.err.println("\n\nStop sending Strings to CloudTurbine");
    	
    	bCTrunning = false;
    	
    	// shut down WriteTask
    	if (writeTaskThread != null) {
    		try {
    			System.err.println("Wait for WriteTask to stop");
    			writeTaskThread.join(500);
    			if (writeTaskThread.isAlive()) {
    				// WriteTask must be waiting to take another screencap off the queue;
    				// interrupt it
    				writeTaskThread.interrupt();
    				writeTaskThread.join(500);
    			}
    			if (!writeTaskThread.isAlive()) {
    				System.err.println("WriteTask has stopped");
    			}
    		} catch (InterruptedException ie) {
    			System.err.println("Caught exception trying to stop WriteTask:\n" + ie);
    		}
    		writeTaskThread = null;
    		writeTask = null;
    	}
    	
    	// shut down CTwriter
    	if (ctw != null) {
    		System.err.println("Close CTwriter");
    		ctw.close();
    		ctw = null;
    	}
    	
    	if (queue != null) {
    		queue.clear();
    		queue = null;
    	}
    	
    }
	
    /**
     * Exit the application.
     */
	private void exit() {
        
		// Turn off streaming
		stopTextToCT();
		
		cttextGuiFrame.setVisible(false);
        
		System.err.println("Exiting...");
		System.exit(0);
        
	}

}
