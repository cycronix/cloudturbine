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

package erigo.ctscreencap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

//
// Define a modal dialog which will contain a panel which covers the entire
// screen and make it translucent (so user can see what is behind this
// panel).  User selects the desired region to capture by drawing a
// rectangle on the panel and then hitting the Enter key.
//
// The code to draw the rectangle specified by the mouse is implemented by
// class CapturePanel, which is taken from:
// http://stackoverflow.com/questions/15776549/create-rectangle-with-mouse-drag-not-draw
//

public class DefineCaptureRegion extends JDialog {
	
	private static final long serialVersionUID = 1L;
	public CTscreencap cts = null;
	
	public DefineCaptureRegion(CTscreencap ctsI) {
		super();
		cts = ctsI;
		// Don't have any border/frame around the window
		setUndecorated(true);
		setForeground(new Color(0,0,0));
		// Cover the entire window
		Rectangle frameRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        setSize(frameRect.width,frameRect.height);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        // Set the dialog to 40% opaque (60% translucent).
        setOpacity(0.40f);
        setAlwaysOnTop(true);
        setModal(true);
        // Add a panel to the dialog which will capture mouse events
        getContentPane().add(new CapturePanel(this));
	}
	
	//
	// Class to allow the user to specify the rectangle they want to capture
	// This was taken from:
	// http://stackoverflow.com/questions/15776549/create-rectangle-with-mouse-drag-not-draw
	//
	
	public class CapturePanel extends JPanel {
		
		private static final long serialVersionUID = 1L;
		private Rectangle selectionBounds;
        private Point clickPoint,currentPoint;
        private DefineCaptureRegion parentDialog = null;
        
        public CapturePanel(DefineCaptureRegion parentDialogI) {
            setOpaque(false);
            parentDialog = parentDialogI;
            
            // Specify GridBagLayout for adding the 2 labels
            GridBagLayout gbl = new GridBagLayout();
    		this.setLayout(gbl);
    		GridBagConstraints gbc = new GridBagConstraints();
    		gbc.anchor = GridBagConstraints.CENTER;
    		gbc.fill = GridBagConstraints.HORIZONTAL;
    		gbc.weightx = 0;
    		gbc.weighty = 0;
    		gbc.insets = new Insets(0,0,0,0);
            
        	JLabel label1 = new JLabel("Click + drag mouse to select region to capture.",JLabel.CENTER);
            label1.setFont(new Font(label1.getFont().getName(),Font.BOLD,32));
            label1.setForeground(Color.RED);
            Utility.add(this,label1,gbl,gbc,0,0,1,1);
            JLabel label2 = new JLabel("Hit Enter key to continue or double-click to quit.",JLabel.CENTER);
            label2.setFont(new Font(label2.getFont().getName(),Font.BOLD,32));
            label2.setForeground(Color.RED);
            Utility.add(this,label2,gbl,gbc,0,1,1,1);
        	
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                	// double click to exit program
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        System.exit(0);
                    }
                }
                
                @Override
                public void mousePressed(MouseEvent e) {
                    clickPoint = e.getPoint();
                    selectionBounds = null;
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    clickPoint = null;
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    Point dragPoint = e.getPoint();
                    currentPoint = dragPoint;
                    int x = Math.min(clickPoint.x, dragPoint.x);
                    int y = Math.min(clickPoint.y, dragPoint.y);
                    int width = Math.max(clickPoint.x - dragPoint.x, dragPoint.x - clickPoint.x);
                    int height = Math.max(clickPoint.y - dragPoint.y, dragPoint.y - clickPoint.y);
                    selectionBounds = new Rectangle(x, y, width, height);
                    repaint();
                }
                
                @Override
                public void mouseMoved(MouseEvent e) {
                	// We draw the cursor at the current mouse point
                	currentPoint = e.getPoint();
                	repaint();
                }
            };
            
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            
            KeyListener panelKeyHandler = new KeyAdapter() {
            	
                @Override
                public void keyTyped(KeyEvent e) {
                	if ( (selectionBounds != null) && (e.getKeyChar() == KeyEvent.VK_ENTER) ) {
                		// We're ready to start capturing the screen
                		parentDialog.cts.regionToCapture = selectionBounds;
                		parentDialog.setVisible(false);
                	}
                }
            };
            
            addKeyListener(panelKeyHandler);
            
            setFocusable(true);
            requestFocusInWindow();
            
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(Color.BLACK);
            Stroke oldStroke = g2d.getStroke();
            int strokeThickness = 3;
        	g2d.setStroke(new BasicStroke(strokeThickness));
            if (selectionBounds != null) {
            	// Draw a rectangle around the currently selected region
            	g2d.draw(selectionBounds);
            }
            if (currentPoint != null) {
            	// Draw cross hairs for the mouse point
            	g2d.drawLine(0, (int)currentPoint.getY(), 10000, (int)currentPoint.getY());
            	g2d.drawLine((int)currentPoint.getX(), 0, (int)currentPoint.getX(), 10000);
            }
            g2d.setStroke(oldStroke);
            g2d.dispose();
        }
    }
	
}
