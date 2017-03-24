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

/*
 * FilePumpSettings
 * 
 * Store settings used to configure FilePump as well as a dialog box for
 * editing the settings.
 *
 */

import java.awt.Color;
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

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class FilePumpSettings extends JDialog implements ActionListener,ItemListener {
    
	private static final long serialVersionUID = 1L;
	
	public enum FileMode {
	    LOCAL_FILESYSTEM, FTP, SFTP
	}
	
	private JFrame parent = null;
	
	private boolean bShowGUI = true;
	
	// Settings
	private String outputFolder = "";					// Where to write the files to
	private double filesPerSec = 1.0;					// How many files per second the pump should output
	private int totNumFiles = 1000;						// Total number of files to write out; -1 indicates unlimited
	private FileMode mode = FileMode.LOCAL_FILESYSTEM;	// Specifies how files will be written out
	private String ftpHost = "";						// FTP hostname
	private String ftpUser = "";						// FTP username
	private String ftpPassword = "";					// FTP password
	
	// Backup copy of the settings (used when user is editing the settings)
	private String orig_outputFolder;
	private double orig_filesPerSec;
	private int orig_totNumFiles;
	private FileMode orig_mode;
	private String orig_ftpHost;
	private String orig_ftpUser;
	private String orig_ftpPassword;
	
	// Dialog components
	private JTextField outputFolderTF = null;
	private JTextField filesPerSecTF = null;
	private JTextField totNumFilesTF = null;
	private JRadioButton localFilesystemRB = null;
	private JRadioButton ftpRB = null;
	private JRadioButton sftpRB = null;
	private ButtonGroup fileModeButtonGroup = null;
	private JLabel ftpHostLabel = null;
	private JTextField ftpHostTF = null;
	private JLabel ftpUserLabel = null;
	private JTextField ftpUserTF = null;
	private JLabel ftpPasswordLabel = null;
	private JPasswordField ftpPasswordTF = null;
	private JButton okButton = null;
	private JButton cancelButton = null;
	
	// Specify if the user clicked the OK button to pop down this dialog
	public boolean bClickedOK = false;
	
	/*
	 * Constructor used when we are running headless.
	 */
	public FilePumpSettings(String outputFolderI, double filesPerSecI, int totNumFilesI, FileMode modeI, String ftpHostI, String ftpUserI, String ftpPasswordI) {
		bShowGUI = false;
		setInitialValues(outputFolderI, filesPerSecI, totNumFilesI, modeI, ftpHostI, ftpUserI, ftpPasswordI);
	}
	
	/*
	 * Constructor
	 * 
	 * Create the dialog but don't display it (gets displayed when user selects "Settings..." menu item)
	 * 
	 */
	public FilePumpSettings(JFrame parentI, String outputFolderI, double filesPerSecI, int totNumFilesI, FileMode modeI, String ftpHostI, String ftpUserI, String ftpPasswordI) {
		
		super(parentI,"FilePump Settings",true);
		
		parent = parentI;
		
		bShowGUI = true;
		
		setInitialValues(outputFolderI, filesPerSecI, totNumFilesI, modeI, ftpHostI, ftpUserI, ftpPasswordI);
		
		setFont(new Font("Dialog", Font.PLAIN, 12));
		GridBagLayout gbl = new GridBagLayout();
		JPanel guiPanel = new JPanel(gbl);
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		
		// Create GUI components
		outputFolderTF = new JTextField(25);
		filesPerSecTF = new JTextField(10);
		totNumFilesTF = new JTextField(10);
		localFilesystemRB = new JRadioButton("Write to local filesystem");
		localFilesystemRB.addItemListener(this);
		ftpRB = new JRadioButton("FTP");
		ftpRB.addItemListener(this);
		sftpRB = new JRadioButton("SFTP");
		sftpRB.addItemListener(this);
		fileModeButtonGroup = new ButtonGroup();
		fileModeButtonGroup.add(localFilesystemRB);
		fileModeButtonGroup.add(ftpRB);
		fileModeButtonGroup.add(sftpRB);
		ftpHostLabel = new JLabel("Host",SwingConstants.LEFT);
		ftpHostTF = new JTextField(20);
		ftpUserLabel = new JLabel("Username",SwingConstants.LEFT);
		ftpUserTF = new JTextField(15);
		ftpPasswordLabel = new JLabel("Password",SwingConstants.LEFT);
		ftpPasswordTF = new JPasswordField(15);
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
		Utility.add(guiPanel,outputFolderTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 2
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Files per second",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,filesPerSecTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 3
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		tempLabel = new JLabel("Total num files",SwingConstants.LEFT);
		gbc.insets = new Insets(10,15,0,10);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,1,1);
		gbc.insets = new Insets(10,0,0,15);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		Utility.add(guiPanel,totNumFilesTF,gbl,gbc,1,row,1,1);
		row++;
		
		// ROW 4
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 100;
		gbc.weighty = 0;
		tempLabel = new JLabel("Leave blank to specify an unlimited number of files.",SwingConstants.CENTER);
		Font labelFont = new Font(tempLabel.getFont().getName(), Font.ITALIC, tempLabel.getFont().getSize());
		tempLabel.setFont(labelFont);
		tempLabel.setForeground(Color.RED);
		gbc.insets = new Insets(0,15,0,15);
		Utility.add(guiPanel,tempLabel,gbl,gbc,0,row,2,1);
		gbc.anchor = GridBagConstraints.WEST;
		row++;
		
		// ROW 5
		JPanel rbPanel = new JPanel(new FlowLayout(FlowLayout.LEADING,5,0));
		rbPanel.add(localFilesystemRB);
		rbPanel.add(ftpRB);
		rbPanel.add(sftpRB);
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.insets = new Insets(10,5,0,15);
		Utility.add(guiPanel,rbPanel,gbl,gbc,0,row,2,1);
		row++;
		
		// ROW 6: A panel containing the FTP parameters (host, username, password)
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
	 * Set initial values.
	 */
	private void setInitialValues(String outputFolderI, double filesPerSecI, int totNumFilesI, FileMode modeI, String ftpHostI, String ftpUserI, String ftpPasswordI) {
		outputFolder = outputFolderI;
		filesPerSec = filesPerSecI;
		totNumFiles = totNumFilesI;
		mode = modeI;
		ftpHost = ftpHostI;
		ftpUser = ftpUserI;
		ftpPassword = ftpPasswordI;
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
	
	/**
	 * 
	 * Accessor method for filesPerSec
	 * @return the filesPerSec
	 */
	public double getFilesPerSec() {
		return filesPerSec;
	}
	
	/**
	 * 
	 * Mutator method for filesPerSec
	 * @param filesPerSecI the filesPerSec to set
	 */
	public void setFilesPerSec(double filesPerSecI) {
		filesPerSec = filesPerSecI;
	}
	
	/**
	 * 
	 * Accessor method for totNumFiles
	 * @return the totNumFiles
	 */
	public int getTotNumFiles() {
		return totNumFiles;
	}
	
	/**
	 * 
	 * Mutator method for totNumFiles
	 * @param totNumFilesI the totNumFiles to set
	 */
	public void setTotNumFiles(int totNumFilesI) {
		totNumFiles = totNumFilesI;
	}
	
	/**
	 * 
	 * Accessor method for mode
	 * @return the mode
	 */
	public FileMode getMode() {
		return mode;
	}
	
	/**
	 * 
	 * Mutator method for mode
	 * @param modeI the mode to set
	 */
	public void setMode(FileMode modeI) {
		mode = modeI;
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
	 * Determine if appropriate settings have been made such that we can start the file pump.
	 */
	public String canPumpRun() {
		
		// Check outputFolder
		if ( (outputFolder == null) || (outputFolder.length() == 0) ) {
			return "You must specify an output directory.";
		}
		if (mode == FileMode.LOCAL_FILESYSTEM) {
			File tempFile = new File(outputFolder);
			if ( (!tempFile.exists()) || (!tempFile.isDirectory()) ) {
				return "You must specify the path to an existing output folder.";
			}
		}
		
		// Check filesPerSec
		if ( (filesPerSec < 0.1) || (filesPerSec > 1000.0) ) {
        	return "Files per second must be a number in the range 0.1 <= files per sec <= 1000";
        }
		
		// Check totNumFiles
		if ( (totNumFiles != -1) && (totNumFiles < 1) ) {
			return "Total num files must be blank (indicating unlimited number of files) or else be an integer greater than or equal to 1.";
		}
		
		// Check FTP parameters, when using FTP
		if ( (mode == FileMode.FTP) || (mode == FileMode.SFTP) ) {
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
		
		return "";
	}
	
	public void popupSettingsDialog() {
		
		if (!bShowGUI) {
			return;
		}
		
		// Squirrel away copies of the current data
		orig_outputFolder = outputFolder;
		orig_filesPerSec = filesPerSec;
		orig_totNumFiles = totNumFiles;
		orig_mode = mode;
		orig_ftpHost = ftpHost;
		orig_ftpUser = ftpUser;
		orig_ftpPassword = ftpPassword;
		
		// Initialize data in the dialog
		outputFolderTF.setText(outputFolder);
		filesPerSecTF.setText(Double.toString(filesPerSec));
		if (totNumFiles == Integer.MAX_VALUE) {
			totNumFilesTF.setText("");
		} else {
			totNumFilesTF.setText(Integer.toString(totNumFiles));
		}
		ftpHostTF.setText(ftpHost);
		ftpUserTF.setText(ftpUser);
		ftpPasswordTF.setText(ftpPassword);
		if (mode == FileMode.LOCAL_FILESYSTEM) {
			localFilesystemRB.setSelected(true);
			ftpRB.setSelected(false);
			sftpRB.setSelected(false);
			ftpHostLabel.setEnabled(false);
			ftpHostTF.setEnabled(false);
			ftpUserLabel.setEnabled(false);
			ftpUserTF.setEnabled(false);
			ftpPasswordLabel.setEnabled(false);
			ftpPasswordTF.setEnabled(false);
		} else if (mode == FileMode.FTP) {
			localFilesystemRB.setSelected(false);
			ftpRB.setSelected(true);
			sftpRB.setSelected(false);
			ftpHostLabel.setEnabled(true);
			ftpHostTF.setEnabled(true);
			ftpUserLabel.setEnabled(true);
			ftpUserTF.setEnabled(true);
			ftpPasswordLabel.setEnabled(true);
			ftpPasswordTF.setEnabled(true);
		} else if (mode == FileMode.SFTP) {
			localFilesystemRB.setSelected(false);
			ftpRB.setSelected(false);
			sftpRB.setSelected(true);
			ftpHostLabel.setEnabled(true);
			ftpHostTF.setEnabled(true);
			ftpUserLabel.setEnabled(true);
			ftpUserTF.setEnabled(true);
			ftpPasswordLabel.setEnabled(true);
			ftpPasswordTF.setEnabled(true);
		}
		
		// Pop up the dialog centered on the parent frame
		pack();
		setLocationRelativeTo(parent);
		setVisible(true); // this method won't return until setVisible(false) is called
		
	}
	
	/*
	 * Respond to changes with the file mode radio buttons
	 */
	@Override
	public void itemStateChanged(ItemEvent eventI) {
		
		Object source = eventI.getSource();
		
		if (source == null) {
			return;
		} else if (source == localFilesystemRB) {
			boolean bSelected = localFilesystemRB.isSelected();
			ftpHostLabel.setEnabled(!bSelected);
			ftpHostTF.setEnabled(!bSelected);
			ftpUserLabel.setEnabled(!bSelected);
			ftpUserTF.setEnabled(!bSelected);
			ftpPasswordLabel.setEnabled(!bSelected);
			ftpPasswordTF.setEnabled(!bSelected);
		} else if ( (source == ftpRB) || (source == sftpRB) ) {
			boolean bSelected = ((JRadioButton)source).isSelected();
			ftpHostLabel.setEnabled(bSelected);
			ftpHostTF.setEnabled(bSelected);
			ftpUserLabel.setEnabled(bSelected);
			ftpUserTF.setEnabled(bSelected);
			ftpPasswordLabel.setEnabled(bSelected);
			ftpPasswordTF.setEnabled(bSelected);
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
		outputFolder = outputFolderTF.getText().trim();
		String filesPerSecStr = filesPerSecTF.getText().trim();
		try {
			filesPerSec = Double.parseDouble(filesPerSecStr);
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Files per second must be a number in the range 0.1 <= files per sec <= 1000", "Settings error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		String totNumFilesStr = totNumFilesTF.getText().trim();
		if (totNumFilesStr.isEmpty()) {
			totNumFiles = Integer.MAX_VALUE;
		} else {
			try {
				totNumFiles = Integer.parseInt(totNumFilesStr);
				if (totNumFiles == -1) {
					totNumFiles = Integer.MAX_VALUE;
				}
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(this, "Total num files must be blank (indicating unlimited number of files) or else be an integer greater than or equal to 1.", "Settings error", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		mode = FileMode.LOCAL_FILESYSTEM;
		if (localFilesystemRB.isSelected()) {
			mode = FileMode.LOCAL_FILESYSTEM;
		} else if (ftpRB.isSelected()) {
			mode = FileMode.FTP;
		} else if (sftpRB.isSelected()) {
			mode = FileMode.SFTP;
		}
		ftpHost = ftpHostTF.getText().trim();
		ftpUser = ftpUserTF.getText().trim();
		char[] passwordCharArray = ftpPasswordTF.getPassword();
		ftpPassword = new String(passwordCharArray).trim();
		
		// Check data
		String errStr = canPumpRun();
		if (!errStr.isEmpty()) {
			// Was a problem with the data the user entered
			JOptionPane.showMessageDialog(this, errStr, "Settings error", JOptionPane.ERROR_MESSAGE);
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
		filesPerSec = orig_filesPerSec;
		totNumFiles = orig_totNumFiles;
		mode = orig_mode;
		ftpHost = orig_ftpHost;
		ftpUser = orig_ftpUser;
		ftpPassword = orig_ftpPassword;
		
		setBClickedOK(false);
		
		// Pop down the dialog
		setVisible(false);
	}
	
	/*
	 * Return a string containing all the FilePumpSettings
	 */
	public String toString() {
		
		StringBuffer sb = new StringBuffer("FilePump settings:\n");
		sb.append("\tOutput folder = " + outputFolder + "\n");
		sb.append("\tFiles per second = " + Double.toString(filesPerSec) + "\n");
		if (totNumFiles == Integer.MAX_VALUE) {
			sb.append("\tUnlimited total number of files\n");
		} else {
			sb.append("\tTotal number of files = " + Integer.toString(totNumFiles) + "\n");
		}
		if (mode == FileMode.LOCAL_FILESYSTEM) {
			sb.append("\tWrite to local filesystem\n");
		} else if (mode == FileMode.FTP) {
			sb.append("\tUsing FTP:\n");
		} else if (mode == FileMode.SFTP) {
			sb.append("\tUsing SFTP:\n");
		}
		if ( (mode == FileMode.FTP) || (mode == FileMode.SFTP) ) {
			sb.append("\t\tHost = " + ftpHost + "\n");
			sb.append("\t\tUsername = " + ftpUser + "\n");
			sb.append("\t\tPassword = " + ftpPassword + "\n");
		}
		
		return sb.toString();
		
	}
	
}
