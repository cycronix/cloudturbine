/**
 * DisplayPlot
 * Pop up a plot using JFreeChart API
 * <p>
 * @author John P. Wilson (JPW), Erigo Technologies
 * @version 01/17/2017
 * 
*/

/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package erigo.filewatch;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Ellipse2D;

/*
 * Copyright 2015-2017 Erigo Technologies LLC
 */

/*
 * This code is based on the "Function2DDemo1" example provided by
 * David Gilbert (JFreeChart Project Leader) at:
 * http://www.jfree.org/phpBB2/viewtopic.php?p=61448
 * 
 * Copyright header from this function:
 * --------------------
 * Function2DDemo1.java
 * --------------------
 * (C) Copyright 2007, by Object Refinery Limited.
 * 
 * This function is also based on the example at CodeJava:
 * http://www.codejava.net/java-se/graphics/using-jfreechart-to-draw-xy-line-chart-with-xydataset
 */

import java.util.List;
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
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

public class DisplayPlot extends ApplicationFrame {
    
    /**
	 * 
	 */
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
     */
    public DisplayPlot(String chartTitleI,String xaxisLabelI,String yaxisLabelI,List<Double> xDataI,List<Double> yDataI) {
        super("FileWatch data");
        chartTitle = chartTitleI;
        xaxisLabel = xaxisLabelI;
        yaxisLabel = yaxisLabelI;
        xData = xDataI;
        yData = yDataI;
        XYDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        JPanel chartPanel =  new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(650, 400));
        setContentPane(chartPanel);
        pack();
        RefineryUtilities.centerFrameOnScreen(this);
        setVisible(true);
    }
    
    /**
     * Add all the data to a new XYDataset
     */
    XYDataset createDataset() {
    	XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries("first dataset");
		for (int i=0; i<xData.size(); ++i) {
			series.add(xData.get(i).doubleValue(),yData.get(i).doubleValue());
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
    JFreeChart createChart(XYDataset dataset) {
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
		
		// XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		// For XYLineAndShapeRenderer: set thickness for series (using strokes)
		// renderer.setSeriesStroke(0, new BasicStroke(1.0f));
		
    	XYShapeRenderer renderer = new XYShapeRenderer();
    	renderer.setSeriesShape(0, new Ellipse2D.Double(-1,-1,2,2));
		
		// sets paint color
		renderer.setSeriesPaint(0, Color.ORANGE);
		
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
    
}
