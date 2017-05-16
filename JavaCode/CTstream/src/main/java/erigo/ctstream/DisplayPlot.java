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

/**
 * DisplayPlot
 * Pop up a plot using JFreeChart API
 * This class was originally taken from FileWatch
 * <p>
 * @author John P. Wilson (JPW), Erigo Technologies
 * @version 05/12/2017
 *
 */

package erigo.ctstream;

/*
 * DisplayPlot is based on the following examples from the web:
 *
 * 1. "Function2DDemo1" example provided by
 *    David Gilbert (JFreeChart Project Leader) at:
 *    http://www.jfree.org/phpBB2/viewtopic.php?p=61448
 *    Copyright header from this class:
 *    --------------------
 *    Function2DDemo1.java
 *    --------------------
 *    (C) Copyright 2007, by Object Refinery Limited.
 *
 * 2. Example from CodeJava:
 *    http://www.codejava.net/java-se/graphics/using-jfreechart-to-draw-xy-line-chart-with-xydataset
 *
 * For further information about JFreeChart:
 * - Javadoc: http://www.jfree.org/jfreechart/api/javadoc/index.html
 * - Homepage: http://www.jfree.org/jfreechart/
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYShapeRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class DisplayPlot extends JFrame {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    JFreeChart chart = null;
    private String chartTitle;
    private String xaxisLabel;
    private String yaxisLabel;

    /**
     * Creates a new plot.
     *
     * @param chartTitleI	Chart title
     * @param xaxisLabelI	Label for x-axis
     * @param yaxisLabelI	Label for y-axis
     * @param xDataI		x-axis data
     * @param yDataI		y-axis data
     * @param xLocI			x-pos of lower left corner of the JFrame
     * @param yLocI			y-pos of lower left corner of the JFrame
     */
    public DisplayPlot(String chartTitleI,String xaxisLabelI,String yaxisLabelI,List<Double> xDataI,List<Double> yDataI,int xLocI,int yLocI) {
        super("FileWatch data");
        chartTitle = chartTitleI;
        xaxisLabel = xaxisLabelI;
        yaxisLabel = yaxisLabelI;
        XYDataset dataset = createDataset(xDataI,yDataI);
        chart = createChart(dataset);
        JPanel chartPanel =  new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(650, 400));
        add(chartPanel, BorderLayout.CENTER);
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Position plot
        // setLocationRelativeTo(null);  // to place in center of screen; not good if there are multiple plots
        setLocation(xLocI,yLocI);
    }

    /**
     * Add all the data to a new XYDataset
     */
    private XYDataset createDataset(List<Double> xDataI,List<Double> yDataI) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("first dataset");
        for (int i=0; i<xDataI.size(); ++i) {
            series.add(xDataI.get(i).doubleValue(),yDataI.get(i).doubleValue());
        }
        dataset.addSeries(series);
        return dataset;
    }

    /**
     * Creates a chart.
     *
     * @param dataset  the dataset.
     *
     * @return A chart instance.
     */
    private JFreeChart createChart(XYDataset dataset) {
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
     * Update data in the plot
     * @param xDataI  New x data
     * @param yDataI  New y data
     */
    public void updateDataset(List<Double> xDataI,List<Double> yDataI) {
        XYDataset dataset = createDataset(xDataI,yDataI);
        chart.getXYPlot().setDataset(dataset);
    }

}
