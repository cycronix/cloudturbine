// MJM 4/20/2017:  experimental test of local window image display

package erigo.ctscreencap;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

public class DisplayImage {

	public JFrame frame = null;
	private JLabel lbl = null;

	public DisplayImage(String title, Dimension initSize)
	{
		// For thread safety: Schedule a job for the event-dispatching thread to create and show the GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				frame = new JFrame();
				frame.setTitle(title);
				lbl = new JLabel();
				JScrollPane scrollPane = new JScrollPane(lbl);
				scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
				frame.add(scrollPane,BorderLayout.CENTER);
				frame.setVisible(true);
				frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
				frame.setSize(initSize);
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
}