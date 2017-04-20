// MJM 4/20/2017:  experimental test of local window image display

package erigo.ctscreencap;

import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class DisplayImage {

	JLabel lbl=null;
	JFrame frame=null;

	public DisplayImage(String title) 
	{
		frame=new JFrame();
		frame.setTitle(title);
		frame.setLayout(new FlowLayout());
//		frame.setSize(width,height);
		lbl = new JLabel();
		frame.add(lbl);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	public void updateImage(BufferedImage img, int width, int height) {
		frame.setSize(width,height);
		lbl.setIcon(new ImageIcon(img));
	}
}