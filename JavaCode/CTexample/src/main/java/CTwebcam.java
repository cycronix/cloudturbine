

//import java.awt.FlowLayout;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

public class CTwebcam {

	private static final class Capture extends Thread {
		@Override
		public void run() {

			Webcam webcam = Webcam.getDefault();
			webcam.setViewSize(WebcamResolution.VGA.getSize());
			webcam.open();

			JFrame frame=new JFrame();
			frame.setTitle("webcam capture");
//			frame.setLayout(new FlowLayout());
			JLabel lbl = new JLabel();
			frame.add(lbl);
			frame.setVisible(true);
			
			while (true) {
				if (!webcam.isOpen()) break;

				BufferedImage image = webcam.getImage();
				if (image == null) break;

				frame.setSize(image.getWidth(),image.getHeight());
				lbl.setIcon(new ImageIcon(image));
			}
		}
	}

	public static void main(String[] args) throws Throwable {
		new Capture().start();
		Thread.sleep(5 * 60 * 1000); // 5 minutes
		System.exit(1);
	}
	
}
