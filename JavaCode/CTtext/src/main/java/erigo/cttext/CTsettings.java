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

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 *
 * Store settings to be used by CloudTurbine. Also defines
 * a dialog to allow the user to edit these settings.
 *
 */

public class CTsettings extends JDialog implements ActionListener,ItemListener {
    
	private static final long serialVersionUID = 1L;
	
	private static final String[] flushIntervalStrings = { "Max responsiveness", "1sec", "2s", "5s", "10s", "30s", "1min", "2m", "5m" };
	private static final Integer[] flushIntervalInts = { new Integer(-1), new Integer(1), new Integer(2), new Integer(5), new Integer(10), new Integer(30), new Integer(60), new Integer(120), new Integer(300) };
	
	JFrame parent = null;
	
	// Settings
	private String outputFolder = "CTdata" + File.separator + "CTtext";
	private String chanName = "cttext.txt";
	private boolean bUseFTP = false;					// Use FTP to write data out?
	private String ftpHost = "";						// FTP hostname
	private String ftpUser = "";						// FTP username
	private String ftpPassword = "";					// FTP password
	private boolean bManualFlush = false;				// If this is true, user will manually click a "Flush" button at which time the code will putData and flush
														// If this is false, putData is called for every Document edit and the flush interval is specified by:
														//     (1) if flushInterval == -1 (i.e., "max responsiveness") then auto flush interval =  maxResponsivenessFlushLevel
														//     (2) if flushInterval > 0, use this value as the auto flush interval
	private double flushInterval = 10.0;				// Interval (sec) at which to automatically flush data; if value is -1,
														// this is a special case for "max responsiveness": no ZIP data, set auto flush = maxResponsivenessFlushLevel
	private static final double maxResponsivenessFlushInterval = 10.0;
														// If flushInterval == -1 (i.e., "max responsiveness") then auto flush interval =  maxResponsivenessFlushLevel
														// (which is a time in milliseconds); data is not ZIP'ed in this case, so it actually shows up on disk immediately;
														// note that flush interval determines when Blocks are closed and the next Block folder starts.
	private static final int blocksPerSegment = 10;		// Fixed setting; Number of Block folders per Segment; 0 = no segments
	private boolean bUseRelativeTime = true;			// Fixed to true (we no longer use absolute timestamps)
	private boolean bZipData = true;					// Block folders will be zipped?  This will be true for all cases except:
														//     (1) If flush interval is -1 ("Max responsiveness")
														//     (2) When bManualFlush is true (put and flush are done right away, so no need to ZIP since Block only contains 1 data point)
	private boolean bChatMode = false;					// Not currently used
	
	// Backup copy of the settings used when user is editing the settings
	private String orig_outputFolder;
	private String orig_chanName;
	private boolean orig_bUseFTP;
	private String orig_ftpHost;
	private String orig_ftpUser;
	private String orig_ftpPassword;
	private boolean orig_bPutDataWithEachEdit;
	private double orig_flushInterval;
	private boolean orig_bUseRelativeTime;
	private boolean orig_bZipData;
	private boolean orig_bChatMode;
	
	// Dialog components
	private JTextField outputDirTF = null;
	private JTextField chanNameTF = null;
	private JCheckBox bUseFTPCheckB = null;
	private JLabel ftpHostLabel = null;
	private JTextField ftpHostTF = null;
	private JLabel ftpUserLabel = null;
	private JTextField ftpUserTF = null;
	private JLabel ftpPasswordLabel = null;
	private JPasswordField ftpPasswordTF = null;
	private JRadioButton autoFlushRB = null;
	private JRadioButton manualFlushRB = null;
	private ButtonGroup flushButtonGroup = null;
	private JLabel flushIntervalLabel = null;
	private JComboBox<String> flushIntervalComboB = null;
	private JButton okButton = null;
	private JButton cancelButton = null;
	
	// Specify if the user clicked the OK button to pop down this dialog
	public boolean bClickedOK = false;
	
	/*
	 * Constructor
	 * 
	 * Create the dialog but don't display it (gets displayed when user selects "Settings..." menu item)
	 * 
	 */
	public CTsettings(JFrame parentI) {
		
		super(parentI,"CTtext Settings",true);
		
		parent = parentI;
		
		setFont(new Font("Dialog", Font.PLAIN, 12));
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		
		// Create GUI components
		outputDirTF = new JTextField(25);
		chanNameTF = new JTextField(10);
		bUseFTPCheckB = new JCheckBox("Use FTP");
		bUseFTPCheckB.addItemListener(this);
		ftpHostLabel = new JLabel("Host",SwingConstants.LEFT);
		ftpHostTF = new JTextField(20);
		ftpUserLabel = new JLabel("Username",SwingConstants.LEFT);
		ftpUserTF = new JTextField(15);
		ftpPasswordLabel = new JLabel("Password",SwingConstants.LEFT);
		ftpPasswordTF = new JPasswordField(15);
		autoFlushRB = new JRadioButton("Automatic flush at interval");
		autoFlushRB.addItemListener(this);
		manualFlushRB = new JRadioButton("Manual flush on button click");
		flushButtonGroup = new ButtonGroup();
		flushButtonGroup.add(autoFlushRB);
		flushButtonGroup.add(manualFlushRB);
		flushIntervalLabel = new JLabel("Flush interval",SwingConstants.LEFT);
		flushIntervalComboB = new JComboBox<String>(flushIntervalStrings);
		flushIntervalComboB.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXX");
		flushIntervalComboB.setEditable(false);
		okButton = new JButton("OK");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		
		// Add components to the guiPanel
		int row = 0;
		
		// ROW 1
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		JLabel tempLabel = new JLabel("Output folder",SwingConstants.LEFT);
		gbc.insets = new Insets(15,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(15,0,0,15);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		Utility.add(guiPanel,outputDirTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 2
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Channel name",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,chanNameTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 3
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,15);
		Utility.add(guiPanel,bUseFTPCheckB,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 4: A panel containing the FTP parameters (host, username, password)
		GridBagLayout panel_gbl = new GridBagLayout();
		JPanel ftpPanel = new JPanel(panel_gbl);
		GridBagConstraints panel_gbc = new GridBagConstraints();
		panel_gbc.anchor = GridBagConstraints.WEST;
		panel_gbc.fill = GridBagConstraints.NONE;
		panel_gbc.weightx = 0;
		panel_gbc.weighty = 0;
		int panel_row = 0;
		// Panel row 1: host
		panel_gbc.insets = new Insets(0,0,0,10);
		Utility.add(ftpPanel,ftpHostLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(0,0,0,0);
		Utility.add(ftpPanel,ftpHostTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 2: username
		panel_gbc.insets = new Insets(2,0,0,10);
		Utility.add(ftpPanel,ftpUserLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(2,0,0,0);
		Utility.add(ftpPanel,ftpUserTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		panel_row++;
		// Panel row 3: password
		panel_gbc.insets = new Insets(2,0,0,10);
		Utility.add(ftpPanel,ftpPasswordLabel,panel_gbl,panel_gbc,0,panel_row,1,1);
		panel_gbc.insets = new Insets(2,0,0,0);
		Utility.add(ftpPanel,ftpPasswordTF,panel_gbl,panel_gbc,1,panel_row,1,1);
		// Add the panel to guiPanel
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(2,50,0,15);
		Utility.add(guiPanel,ftpPanel,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 5
		JPanel rbPanel = new JPanel(new FlowLayout(FlowLayout.LEADING,5,0));
		rbPanel.add(autoFlushRB);
		rbPanel.add(manualFlushRB);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,5,0,15);
		Utility.add(guiPanel,rbPanel,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 6
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,flushIntervalLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,flushIntervalComboB,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 7
		// Put the command buttons in a JPanel so they are all the same size
        JPanel buttonPanel = new JPanel(new GridLayout(1,2,15,0));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        // Don't have the buttons resize if the dialog is resized
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.ipadx = 20;
        gbc.insets = new Insets(15,25,15,25);
        gbc.anchor = GridBagConstraints.CENTER;
        Utility.add(guiPanel,buttonPanel,gbl,gbc,0,row,2,1);
		
		// Add guiPanel to the content pane of the parent JFrame
		gbl = new GridBagLayout();
		getContentPane().setLayout(gbl);
		gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 100;
		gbc.weighty = 100;
		gbc.insets = new Insets(0,0,0,0);
		Utility.add(getContentPane(),guiPanel,gbl,gbc,0,0,1,1);
		
		pack();
		
		// Handle the close operation in the windowClosing() method of the
		// registered WindowListener object.  This will get around
		// JFrame's default behavior of automatically hiding the window when
		// the user clicks on the '[x]' button.
		setDefaultCloseOperation(
			javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					cancelAction();
				}
			});

	}
	
	/*
	 * Accessor method for outputFolder
	 */
	public String getOutputFolder() {
		return outputFolder;
	}
	
	/*
	 * Mutator method for outputFolder
	 */
	public void setOutputFolder(String outputFolderI) {
		outputFolder = outputFolderI;
	}
	
	/*
	 * Accessor method for chanName
	 */
	public String getChanName() {
		return chanName;
	}
	
	/*
	 * Mutator method for chanName
	 */
	public void setChanName(String chanNameI) {
		chanName = chanNameI;
	}
	
	/*
	 * Accessor method for bUseFTP
	 */
	public boolean getBUseFTP() {
		return bUseFTP;
	}
	
	/*
	 * Mutator method for bUseFTP
	 */
	public void setBUseFTP(boolean bUseFTPI) {
		bUseFTP = bUseFTPI;
	}
	
	/*
	 * Accessor method for ftpHost
	 */
	public String getFTPHost() {
		return ftpHost;
	}
	
	/*
	 * Mutator method for ftpHost
	 */
	public void setFTPHost(String ftpHostI) {
		ftpHost = ftpHostI;
	}
	
	/*
	 * Accessor method for ftpUser
	 */
	public String getFTPUser() {
		return ftpUser;
	}
	
	/*
	 * Mutator method for ftpUser
	 */
	public void setFTPUser(String ftpUserI) {
		ftpHost = ftpUserI;
	}
	
	/*
	 * Accessor method for ftpPassword
	 */
	public String getFTPPassword() {
		return ftpPassword;
	}
	
	/*
	 * Mutator method for ftpPassword
	 */
	public void setFTPPassword(String ftpPasswordI) {
		ftpHost = ftpPasswordI;
	}
	
	/*
	 * Accessor method for bPutDataWithEachEdit
	 */
	public boolean getBManualFlush() {
		return bManualFlush;
	}
	
	/*
	 * Mutator method for bPutDataWithEachEdit
	 */
	public void setBManualFlush(boolean bManualFlushI) {
		bManualFlush = bManualFlushI;
	}
	
	/*
	 * Accessor method for flushInterval
	 */
	public double getFlushInterval() {
		return flushInterval;
	}
	
	/*
	 * Mutator method for flushInterval
	 */
	public void setFlushInterval(double flushIntervalI) {
		flushInterval = flushIntervalI;
	}
	
	/*
	 * Accessor method for maxResponsivenessFlushInterval
	 */
	public double getMaxResponsivenessFlushInterval() {
		return maxResponsivenessFlushInterval;
	}
	
	/*
	 * Accessor method for blocksPerSegment
	 */
	public int getBlocksPerSegment() {
		return blocksPerSegment;
	}
	
	/*
	 * Accessor method for bUseRelativeTime
	 */
	public boolean getBUseRelativeTime() {
		return bUseRelativeTime;
	}
	
	/*
	 * Mutator method for bUseRelativeTime
	 */
	public void setBUseRelativeTime(boolean bUseRelativeTimeI) {
		bUseRelativeTime = bUseRelativeTimeI;
	}
	
	/*
	 * Accessor method for bZipData
	 */
	public boolean getBZipData() {
		return bZipData;
	}
	
	/*
	 * Mutator method for bZipData
	 */
	public void setBZipData(boolean bZipDataI) {
		bZipData = bZipDataI;
	}
	
	/*
	 * Accessor method for bChatMode
	 */
	public boolean getBChatMode() {
		return bChatMode;
	}
	
	/*
	 * Mutator method for bChatMode
	 */
	public void setBChatMode(boolean bChatModeI) {
		bChatMode = bChatModeI;
	}
	
	/*
	 * Accessor method for bClickedOK
	 */
	public boolean getBClickedOK() {
		return bClickedOK;
	}
	
	/*
	 * Mutator method for bClickedOK
	 */
	public void setBClickedOK(boolean bClickedOKI) {
		bClickedOK = bClickedOKI;
	}
	
	/*
	 * Determine if appropriate settings have been made such that we can start streaming data.
	 */
	public String canCTrun() {
		
		// Test constants that are defined above
		assert (blocksPerSegment >= 0);
		assert (maxResponsivenessFlushInterval >= 0.0);
		
		// Check outputFolder
		if ( (outputFolder == null) || (outputFolder.length() == 0) ) {
			return "You must specify an output directory.";
		}
		
		// Check chanName
		if ( (chanName == null) || (chanName.length() == 0) ) {
			return "You must specify a channel name";
		}
		if (chanName.contains(" ")) {
			return "You must specify a channel name that does not contain embedded spaces";
		}
		
		// Check FTP parameters, when using FTP
		if (bUseFTP) {
			if ( (ftpHost == null) || (ftpHost.length() == 0) ) {
				return "You must specify the FTP host";
			}
			if (ftpHost.contains(" ")) {
				return "The FTP host name must not contain embedded spaces";
			}
			if ( (ftpUser == null) || (ftpUser.length() == 0) ) {
				return "You must specify the FTP username";
			}
			if (ftpUser.contains(" ")) {
				return "The FTP username must not contain embedded spaces";
			}
			if ( (ftpPassword == null) || (ftpPassword.length() == 0) ) {
				return "You must specify the FTP password";
			}
		}
		
		if ( (flushInterval != -1.0) && (flushInterval < 1.0) ) {
			 return "Error in value of flush interval.";
		}
		
		return "";
	}
	
	public void popupSettingsDialog() {
		
		// Squirrel away copies of the current data
		orig_outputFolder = outputFolder;
		orig_chanName = chanName;
		orig_bUseFTP = bUseFTP;
		orig_ftpHost = ftpHost;
		orig_ftpUser = ftpUser;
		orig_ftpPassword = ftpPassword;
		orig_bPutDataWithEachEdit = bManualFlush;
		orig_flushInterval = flushInterval;
		orig_bUseRelativeTime = bUseRelativeTime;
		orig_bZipData = bZipData;
		orig_bChatMode = bChatMode;
		
		// Initialize data in the dialog
		outputDirTF.setText(outputFolder);
		chanNameTF.setText(chanName);
		bUseFTPCheckB.setSelected(bUseFTP);
		ftpHostTF.setText(ftpHost);
		ftpUserTF.setText(ftpUser);
		ftpPasswordTF.setText(ftpPassword);
		ftpHostLabel.setEnabled(bUseFTP);
		ftpHostTF.setEnabled(bUseFTP);
		ftpUserLabel.setEnabled(bUseFTP);
		ftpUserTF.setEnabled(bUseFTP);
		ftpPasswordLabel.setEnabled(bUseFTP);
		ftpPasswordTF.setEnabled(bUseFTP);
		autoFlushRB.setSelected(!bManualFlush);
		manualFlushRB.setSelected(bManualFlush);
		int selectedIdx = Arrays.asList(flushIntervalInts).indexOf((int)flushInterval);
		flushIntervalComboB.setSelectedIndex(selectedIdx);
		flushIntervalLabel.setEnabled(!bManualFlush);
		flushIntervalComboB.setEnabled(!bManualFlush);
		
		// Pop up the dialog centered on the parent frame
		pack();
		setLocationRelativeTo(parent);
		setVisible(true); // this method won't return until setVisible(false) is called
		
	}
	
	/*
	 * The state of one of the following items has changed:
	 * 1. bUseFTPCheckB check box: Enable or disable FTP setting fields given the state of the "Use FTP" checkbox
	 * 2. autoFlushRB radio button: Enable or disable the Flush interval combo box
	 */
	@Override
	public void itemStateChanged(ItemEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (source == bUseFTPCheckB) {
			boolean bChecked = bUseFTPCheckB.isSelected();
			ftpHostLabel.setEnabled(bChecked);
			ftpHostTF.setEnabled(bChecked);
			ftpUserLabel.setEnabled(bChecked);
			ftpUserTF.setEnabled(bChecked);
			ftpPasswordLabel.setEnabled(bChecked);
			ftpPasswordTF.setEnabled(bChecked);
		} else if (source == autoFlushRB) {
			if (autoFlushRB.isSelected()) {
				flushIntervalLabel.setEnabled(true);
				flushIntervalComboB.setEnabled(true);
			} else {
				flushIntervalLabel.setEnabled(false);
				flushIntervalComboB.setEnabled(false);
			}
		}
		
	}
	
	/*
	 * Handle button callbacks
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 * 
	 */
	@Override
	public void actionPerformed(ActionEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (eventI.getActionCommand().equals("Browse...")) {
			// This was for a "Browse" button to locate a directory, but since
			// we're using this for filesystems and FTP, don't bother
			// Code largely taken from http://stackoverflow.com/questions/4779360/browse-for-folder-dialog
			JFileChooser fc = new JFileChooser();
			fc.setCurrentDirectory(new java.io.File("."));
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = fc.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
			    File selectedFolder = fc.getSelectedFile();
			    try {
					outputDirTF.setText(selectedFolder.getCanonicalPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else if (eventI.getActionCommand().equals("OK")) {
			okAction();
		} else if (eventI.getActionCommand().equals("Cancel")) {
			cancelAction();
		} 
		
	}
	
	/*
	 * User has hit the OK button; save and check data.
	 */
	public void okAction() {
		// Save data from the dialog
		outputFolder = outputDirTF.getText().trim();
		chanName = chanNameTF.getText().trim();
		bUseFTP = bUseFTPCheckB.isSelected();
		ftpHost = ftpHostTF.getText().trim();
		ftpUser = ftpUserTF.getText().trim();
		char[] passwordCharArray = ftpPasswordTF.getPassword();
		ftpPassword = new String(passwordCharArray).trim();
		bManualFlush = manualFlushRB.isSelected();
		flushInterval = (double)flushIntervalInts[ flushIntervalComboB.getSelectedIndex() ];
		if (bManualFlush) {
			bZipData = false;
		} else {
			if ((int)flushInterval == -1) {
				bZipData = false;
			} else {
				bZipData = true;
			}
		}
		
		// Check data
		String errStr = canCTrun();
		if (!errStr.isEmpty()) {
			// Was a problem with the data the user entered
			JOptionPane.showMessageDialog(this, errStr, "CloudTurbine settings error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		setBClickedOK(true);
		
		// Pop down the dialog
		setVisible(false);
	}
	
	/*
	 * User has hit the Cancel button.  Restore original data.
	 */
	public void cancelAction() {
		
		// Restore original values
		outputFolder = orig_outputFolder;
		chanName = orig_chanName;
		bUseFTP = orig_bUseFTP;
		ftpHost = orig_ftpHost;
		ftpUser = orig_ftpUser;
		ftpPassword = orig_ftpPassword;
		bManualFlush = orig_bPutDataWithEachEdit;
		flushInterval = orig_flushInterval;
		bUseRelativeTime = orig_bUseRelativeTime;
		bZipData = orig_bZipData;
		bChatMode = orig_bChatMode;
		
		setBClickedOK(false);
		
		// Pop down the dialog
		setVisible(false);
	}
	
	/*
	 * Return a string containing all the CTsettings
	 */
	public String toString() {
		
		StringBuffer sb = new StringBuffer("CTtext settings:\n");
		sb.append("\tOutput folder = " + outputFolder + "\n");
		sb.append("\tChan name = " + chanName + "\n");
		if (!bUseFTP) {
			sb.append("\tNot using FTP\n");
		} else {
			sb.append("\tUsing FTP:\n");
			sb.append("\t\tHost = " + ftpHost + "\n");
			sb.append("\t\tUsername = " + ftpUser + "\n");
			sb.append("\t\tPassword = " + ftpPassword + "\n");
		}
		if (bManualFlush) {
			sb.append("\tputData and flush when user clicks the Flush button; no ZIP; Block-level folder will contain one Point-level folder\n");
		} else  {
			if ((int)flushInterval == -1) {
				sb.append("\tputData is called with every document change; no ZIP; automatically flush every " + maxResponsivenessFlushInterval + " seconds\n");
			} else {
				sb.append("\tputData is called with every document change; ZIP is on; automatically flush every " + flushInterval + " seconds\n");
			}
		}
		if (blocksPerSegment == 0) {
			sb.append("\tBlocks per Segment = " + blocksPerSegment + ", i.e. no segment folders\n");
		} else {
			sb.append("\tBlocks per Segment = " + blocksPerSegment + "\n");
		}
		sb.append("\tUse relative time = " + bUseRelativeTime + "\n");
		
		return sb.toString();
		
	}
	
}
