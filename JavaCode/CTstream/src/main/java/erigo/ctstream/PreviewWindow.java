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
 * @version 04/27/2017
 *
 */

public class PreviewWindow {

	public JFrame frame = null;
	private JLabel lbl = null;

	public PreviewWindow(String title, Dimension initSize)
	{
		// For thread safety: Schedule a job for the event-dispatching thread to create and show the GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				frame = new JFrame();
				frame.setTitle(title);
				lbl = new JLabel();
				lbl.setFont(new Font(lbl.getFont().getName(), Font.PLAIN, 18));
				JScrollPane scrollPane = new JScrollPane(lbl);
				scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
				frame.add(scrollPane,BorderLayout.CENTER);
				frame.setVisible(true);
				frame.setDefaultCloseOperation(JFrame. DO_NOTHING_ON_CLOSE);
				frame.setSize(initSize);
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
		// For thread safety: Schedule a job for the event-dispatching thread to update the text on the JLabel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// give some padding around the text
				lbl.setText("  " + textI + "  ");
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