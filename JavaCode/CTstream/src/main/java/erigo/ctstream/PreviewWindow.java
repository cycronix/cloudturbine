/*
Copyright 2017 Cycronix

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

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * Popup a window to previewWindow the image that will be sent to CT
 *
 * @author Matt Miller, John Wilson
 * @version 05/12/2017
 *
 */

public class PreviewWindow {

	private JFrame frame = null;		// the preview window
	private boolean bText = false;		// is this frame going to display text or image?
	private JLabel lbl = null;			// for displaying image (in this case, bText must be false)
	private JTextArea textArea = null;	// for displaying text (in this case, bText must be true)

	public PreviewWindow(String title, Dimension initSize, boolean bTextI)
	{
		bText = bTextI;
		// For thread safety: Schedule a job for the event-dispatching thread to create and show the GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				frame = new JFrame(title);
				GridBagLayout gbl = new GridBagLayout();
				GridBagConstraints gbc = new GridBagConstraints();
				gbc.anchor = GridBagConstraints.CENTER;
				gbc.fill = GridBagConstraints.BOTH;
				gbc.weightx = 100;
				gbc.weighty = 100;
				gbc.insets = new Insets(0, 0, 0, 0);
				JPanel panel = new JPanel(gbl);
				// Add the appropriate component (JTextArea or JLabel) to the scroll pane
				JScrollPane scrollPane = null;
				if (bText) {
					textArea = new JTextArea(3,10);
					textArea.setEditable(false);
					scrollPane = new JScrollPane(textArea);
				} else {
					lbl = new JLabel();
					scrollPane = new JScrollPane(lbl);
				}
				scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
				// Add the scroll pane to the panel
				Utility.add(panel, scrollPane, gbl, gbc, 0, 0, 1, 1);
				// Add the panel to the frame
				GridBagLayout framegbl = new GridBagLayout();
				Utility.add(frame, panel, framegbl, gbc, 0, 0, 1, 1);
				frame.pack();
				frame.setSize(initSize);
				frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
				frame.setVisible(true);
			}
		});
	}

	/**
	 * Set the size of the frame.
	 * @param sizeI  Desired size for the preview window.
	 */
	public void setFrameSize(Dimension sizeI) {
		// For thread safety: Schedule a job for the event-dispatching thread to update the image on the JLabel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				frame.setSize(sizeI);
			}
		});
	}

	public void updateImage(BufferedImage img, int width, int height) {
		if (bText) {
			System.err.println("ERROR: preview window was setup for text but now being asked to display an image");
			return;
		}
		// For thread safety: Schedule a job for the event-dispatching thread to update the image on the JLabel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				lbl.setSize(width, height);
				lbl.setIcon(new ImageIcon(img));
				lbl.repaint();
			}
		});
	}

	public void updateText(String textI) {
		if (!bText) {
			System.err.println("ERROR: preview window was setup for an image but now being asked to display text");
			return;
		}
		// For thread safety: Schedule a job for the event-dispatching thread to update the text on the JLabel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// give some padding around the text
				textArea.setText(textI);
			}
		});
	}

	public void close() {
		// For thread safety: Schedule a job for the event-dispatching thread to bring down the existing preview window
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				frame.setVisible(false);
			}
		});
	}
}