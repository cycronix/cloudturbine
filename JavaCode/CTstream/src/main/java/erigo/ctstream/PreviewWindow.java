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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * A preview window for displaying data to be sent to CT.
 *
 * @author Matt Miller, John Wilson
 * @version 05/16/2017
 *
 */

public class PreviewWindow {

	public enum PreviewType { IMAGE, PLOT, TEXT }

	private JFrame frame = null;		// the preview window
	private PreviewType previewType;	// the type of data to display
	private JLabel lbl = null;			// for displaying image
	private JFreeChart chart = null;	// for displaying plot
	private JTextArea textArea = null;	// for displaying text

	/**
	 * Create a new preview window.
	 *
	 * @param title          Title displayed in the preview window's title bar.
	 * @param initSize       Initial size of the preview window.
	 * @param previewTypeI   Type of preview window; must be one of PreviewType.
	 */
	public PreviewWindow(String title, Dimension initSize, PreviewType previewTypeI)
	{
		previewType = previewTypeI;
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
				switch (previewType) {
					case IMAGE:
						lbl = new JLabel();
						scrollPane = new JScrollPane(lbl);
						break;
					case PLOT:
						// create chart with default datapoint
						List<Double> xdata = new ArrayList<Double>();
						List<Double> ydata = new ArrayList<Double>();
						xdata.add(0.0);
						ydata.add(0.0);
						chart = createChart(createDataset(xdata,ydata),"", "Time (sec)", "");
						JPanel chartPanel =  new ChartPanel(chart);
						// chartPanel.setPreferredSize(new java.awt.Dimension(650, 400));
						scrollPane = new JScrollPane(chartPanel);
						break;
					case TEXT:
						textArea = new JTextArea(3,10);
						textArea.setEditable(false);
						scrollPane = new JScrollPane(textArea);
						break;
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

	/**
	 * Put a new image in the preview window.
	 * @param img       The new image.
	 */
	public void updateImage(BufferedImage img) {
		if (previewType != PreviewType.IMAGE) {
			System.err.println("ERROR: preview window not setup for image");
			return;
		}
		int width = img.getWidth();
		int height = img.getHeight();
		// For thread safety: Schedule a job for the event-dispatching thread to update the image on the JLabel
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				lbl.setSize(width, height);
				lbl.setIcon(new ImageIcon(img));
				lbl.repaint();
			}
		});
	}

	public void updatePlot(java.util.List<Double> xDataI, java.util.List<Double> yDataI, boolean bMakeSymmetricI) {
		if (previewType != PreviewType.PLOT) {
			System.err.println("ERROR: preview window not setup for plot");
			return;
		}
		if ( (xDataI == null) || xDataI.isEmpty() || (yDataI == null) || yDataI.isEmpty() ) {
			System.err.println("Updating preview plot: empty x and/or y data, no data to display");
			return;
		}
		XYDataset dataset = createDataset(xDataI, yDataI);
		if (chart == null) {
			System.err.println("Updating preview plot: chart is currently null (probably a startup transient)");
			return;
		}
		chart.getXYPlot().setDataset(dataset);
		if (bMakeSymmetricI) {
			// Adjust the y-axis (Range) limits
			double maxVal = 0.0;
			for (double nextDouble : yDataI) {
				if (Math.abs(nextDouble) > maxVal) maxVal = Math.abs(nextDouble);
			}
			double axisLimit = 0.0;
			int powerOfTen = -10;
			while (true) {
				double baseVal = Math.pow(10.0,(double)powerOfTen);
				if (maxVal < baseVal) {
					axisLimit = baseVal;
					break;
				}
				else if (maxVal < 2.0*baseVal) {
					axisLimit = 2.0*baseVal;
					break;
				}
				else if (maxVal < 5.0*baseVal) {
					axisLimit = 5.0*baseVal;
					break;
				}
				powerOfTen = powerOfTen + 1;
			}
			chart.getXYPlot().getRangeAxis().setRange(-1*axisLimit, axisLimit);
		}
	}

	/**
	 * Creates a chart.
	 *
	 * @param dataset  the dataset.
	 *
	 * @return A chart instance.
	 */
	private JFreeChart createChart(XYDataset dataset, String chartTitle, String xaxisLabel, String yaxisLabel) {
		// create the chart...
		JFreeChart chart = ChartFactory.createXYLineChart(
				chartTitle,
				xaxisLabel,
				yaxisLabel,
				dataset,
				PlotOrientation.VERTICAL,
				false,  // include legend
				true,   // tooltips
				false   // urls
		);

		customizeChart(chart);

		XYPlot plot = (XYPlot) chart.getPlot();
		// Domain is the x-axis, range is the y-axis
		// Can set "margins" at each end of each axis - this specifies space to increase/decrease the displayed
		// values at each end; margins are specified in terms of a percentage.
		plot.getDomainAxis().setLowerMargin(0.0);
		plot.getDomainAxis().setUpperMargin(0.0);

		return chart;
	}

	/*
     * Customize the lines, symbols, gridlines, etc.
     *
     * Many of these options we'll just use the default
     */
	private void customizeChart(JFreeChart chart) {

		XYPlot plot = chart.getXYPlot();

		// XYLineAndShapeRenderer does both lines and shapes
		// XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		// For XYLineAndShapeRenderer: set thickness for series (using strokes)
		// renderer.setSeriesStroke(0, new BasicStroke(1.0f));

		// XYShapeRenderer does shapes only
		XYShapeRenderer renderer = new XYShapeRenderer();
		renderer.setSeriesShape(0, new Ellipse2D.Double(-0.75,-0.75,1.5,1.5));

		// sets paint color
		renderer.setSeriesPaint(0, Color.RED);

		// sets paint color for plot outlines (chart borders)
		// plot.setOutlinePaint(Color.BLUE);
		// plot.setOutlineStroke(new BasicStroke(2.0f));

		// sets renderer for lines
		plot.setRenderer(renderer);

		// sets plot background
		// plot.setBackgroundPaint(Color.DARK_GRAY);

		// sets paint color for the grid lines
		// plot.setRangeGridlinesVisible(true);
		// plot.setRangeGridlinePaint(Color.BLACK);

		// plot.setDomainGridlinesVisible(true);
		// plot.setDomainGridlinePaint(Color.BLACK);

	}

	/**
	 * Create a dataset to be displayed by the plot
	 * @param xDataI   New x data
	 * @param yDataI   Mew y data
	 * @return the new XYDataset
	 */
	private XYDataset createDataset(java.util.List<Double> xDataI, java.util.List<Double> yDataI) {
		XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries("first dataset");
		for (int i=0; i<xDataI.size(); ++i) {
			series.add(xDataI.get(i).doubleValue(),yDataI.get(i).doubleValue());
		}
		dataset.addSeries(series);
		return dataset;
	}

	public void updateText(String textI) {
		if (previewType != PreviewType.TEXT) {
			System.err.println("ERROR: preview window not setup for text");
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