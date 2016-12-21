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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class CTtext implements ActionListener {

	// GUI components
	private JFrame cttextGuiFrame = null;
	private JTextArea textArea = null;
	private JScrollPane scrollPane = null;
	private JButton manualFlushButton = null;
	
	// CloudTurbine settings
	public CTsettings ctSettings = null;
	public boolean bCTrunning = false;
	
	// Class to handle interaction between the document and CloudTurbine
	private DocumentChangeListener ctDoc = null;
    
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
    
	public CTtext() {
		ctDoc = new DocumentChangeListener(this);
	}
    
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
		
		// DISABLE THE KEY BINDING FOR NOW
		// DON'T NEED TO RESPOND TO LINE FEEDS AS CTwriter WILL EITHER FLUSH CONTENT WITH EVERY EDIT OR ELSE USE ASYNC AUTO FLUSH
		// Add a KeyBinding to the JTextArea so we know when the user hits the ENTER key
		// textArea.getInputMap().put(KeyStroke.getKeyStroke( "ENTER" ),"doEnterAction" );
		// Specify that the action "doEnterAction" will be implemented by ctDoc
		// textArea.getActionMap().put("doEnterAction",ctDoc);
		
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
	
	public JFrame getParentFrame() {
		return cttextGuiFrame;
	}
	
	public JTextArea getTextArea() {
		return textArea;
	}

	public void actionPerformed(ActionEvent eventI) {

		Object source = eventI.getSource();

		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Settings...")) {
			// Turn off streaming
			bCTrunning = false;
			ctDoc.closeStreaming();
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
			// Manually save and flush the document (ie, this ends up calling putData() and flush())
			ctDoc.processText(null);
		} else if (eventI.getActionCommand().equals("Exit")) {
			exit();
		}
		
	}
	
	/*
	 * Setup the user interface to either enable or disable editing
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
	
	private void exit() {
        
		// Turn off streaming
		ctDoc.closeStreaming();
		
		cttextGuiFrame.setVisible(false);
        
		System.err.println("Exiting...");
		System.exit(0);
        
	}

}
