/*
Copyright 2017-2018 Erigo Technologies LLC

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
 * <p>
 * @author John P. Wilson (JPW), Erigo Technologies
 * @version 04/18/2018
 * 
*/

package erigo.filewatch;

/*
 * DisplayPlot has been adapted from the XYLineChartExample code available at:
 *     https://www.boraji.com/jfreechart-xy-line-chart-example
 * Author: Sunil Singh Bora, http://boraji.com/
 * Licensed under a Creative Commons Attribution 4.0 International License, https://creativecommons.org/licenses/by/4.0/legalcode
 * 
 * For further information about JFreeChart:
 * - Javadoc: http://www.jfree.org/jfreechart/api/javadoc/index.html
 * - Homepage: http://www.jfree.org/jfreechart/
 * - GitHub: https://github.com/jfree/jfreechart
 */

import java.util.List;
import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class DisplayPlot extends JFrame {
    
	private static final long serialVersionUID = 1L;
    
	private String chartTitle;
	private String xaxisLabel;
	private String yaxisLabel;
	private List<Double> xData;
	private List<Double> yData;
	
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
        xData = xDataI;
        yData = yDataI;

        // Create dataset
        XYDataset dataset = createDataset();

        // Create chart
        JFreeChart chart = ChartFactory.createXYLineChart(
                chartTitle,
                xaxisLabel,
                yaxisLabel,
                dataset,
                PlotOrientation.VERTICAL,
                false,  // no legend
                true,
                false);

        // Don't have any lower or upper margin; I think this will set the ends of the data
        // right up against the lower and upper edges of the plot
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.getDomainAxis().setLowerMargin(0.0);
        plot.getDomainAxis().setUpperMargin(0.0);

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        setContentPane(panel);
        setSize(800, 400);
        // setLocationRelativeTo(null);  // to place in center of screen; not good if there are multiple plots
        setLocation(xLocI,yLocI);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    }
    
    /**
     * Add all the data to a new XYDataset
     */
    private XYDataset createDataset() {

        XYSeriesCollection dataset = new XYSeriesCollection();

        XYSeries series = new XYSeries("data");
        for (int i=0; i<xData.size(); ++i) {
            series.add(xData.get(i).doubleValue(),yData.get(i).doubleValue());
        }

        //Add series to dataset
        dataset.addSeries(series);

        return dataset;

    }
    
}
