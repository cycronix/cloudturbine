/*
Copyright 2019 Erigo Technologies

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

/*

MouseDataGenerator

Generate JSON data for CTrollaball based on user mouse position.

John Wilson, Erigo Technologies

version: 2019-03-26

*/

package cycronix.cttraveler;

import cycronix.ctlib.CTwriter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MouseDataGenerator implements JsonGenerator {

    // plane altitude (it will be constant)
    private float PLANE_ALT = 5.0f;

    // screen dimensions
    private Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    private double width = screenSize.getWidth();
    private double height = screenSize.getHeight();
    private double PIXEL_SCALE = -1.0;

    // Create buffers of the plane's location and heading; used as follows:
    // 1. to set the Jeep location (which lags behind the plane)
    // 2. to dampen the plane's heading
    //   (so the heading isn't quite so jerky)
    List<Double> xPosList = new ArrayList<>();
    List<Double> yPosList = new ArrayList<>();
    List<Double> headingList = new ArrayList<>();

    private String playerName = null;
    private String modelType = null;

    public MouseDataGenerator(String playerNameI, String modelTypeI) {

        playerName = playerNameI;
        modelType = modelTypeI;

        // Scale both width and height by the same factor
        PIXEL_SCALE = width;
        if (height < width) {
            PIXEL_SCALE = height;
        }

        for (int i = 0; i < 20; ++i) {
            xPosList.add(0.0);
            yPosList.add(0.0);
            headingList.add(0.0);
        }
    }

    //
    // Generate simulated data based on the current mouse position on the desktop.
    //
    public String generateJson(long time_msecI) {

        // Rotate the position lists; we will replace the oldest element
        // (which will have been moved to index=0 location after this rotate operation)
        Collections.rotate(xPosList,1);
        Collections.rotate(yPosList,1);

        // Calculate the new plane x,y position based on mouse position
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        // Normalize x,y position and flip Y position (so bottom=0)
        double x_pt = mousePos.getX() / PIXEL_SCALE;
        double y_pt = (height - mousePos.getY()) / PIXEL_SCALE;
        // Expand location so that and x or y position 0 maps to -1*GAME_FIELD_EXTENT
        // and and x or y position 1 maps to +1*GAME_FIELD_EXTENT
        double GAME_FIELD_EXTENT = 9.75f;
        x_pt = x_pt * 2.0f*GAME_FIELD_EXTENT - GAME_FIELD_EXTENT;
        if (x_pt < (-1f * GAME_FIELD_EXTENT)) {
            // On dual-monitor systems, pos will end up going -1 to +1
            // cut off -1 to 0 so that it stays at -1*GAME_FIELD_EXTENT
            x_pt = -1f * GAME_FIELD_EXTENT;
        }
        y_pt = y_pt * 2.0f*GAME_FIELD_EXTENT - GAME_FIELD_EXTENT;
        xPosList.set(0,x_pt);
        yPosList.set(0,y_pt);

        // Calculate plane heading based on current pos relative to pos Nback samples ago
        int Nback = 5;
        double deltaX = x_pt - xPosList.get(Nback);
        double deltaY = y_pt - yPosList.get(Nback);
        double heading = 90.0 - Math.toDegrees(Math.atan2(deltaY,deltaX));
        if ( (Math.abs(deltaX) < 0.05) && (Math.abs(deltaY) < 0.05) ) {
            heading = headingList.get(0);
        }
        Collections.rotate(headingList,1);
        headingList.set(0,heading);
        double pitch = 0.0;
        double roll = 0.0;

        // Specify the Jeep location/heading; note that it will lag behind the plane's location (use the oldest data in the lists)
        // The jeep is positioned relative to the plane's current location (x_pt,y_pt)
        double jeep_x = xPosList.get(xPosList.size()-1) - x_pt;
        double jeep_y = yPosList.get(xPosList.size()-1) - y_pt;
        double jeep_hdg = headingList.get(xPosList.size()-1);

        //
        // Create the JSON-formatted Unity packet
        //
        double time_sec = time_msecI / 1000.0;
        List<PlayerComponentState> objects = new ArrayList<PlayerComponentState>();
        //
        // Empty base object; plane, jeep, etc. will be relative to this
        //
        objects.add(new PlayerComponentState("Base", null, "Base", true, x_pt, 0.0, y_pt, 0.0, 0.0, 0.0, "", 1.0, null));
        //
        // Plane
        //
        objects.add(new PlayerComponentState("Base" + "/Plane", null, modelType, true, 0.0, PLANE_ALT, 0.0, pitch, heading, roll, "", 1.0, null));
        //
        // Jeep position and heading
        //
        objects.add(new PlayerComponentState("Base" + "/Jeep", null, "Jeep", true, jeep_x, 0.0, jeep_y, 0.0, jeep_hdg, 0.0, "", 1.0, null));
        //
        // Add scan cylinders along 3 trajectories
        //
        double cyl_xpos;
        double cyl_ypos;
        double cyl_height;
        // Lines are created as a series of points (in this case, connecting the centers of the cylinders along each trajectory)
        String lineAStr = "";
        String lineBStr = "";
        String lineCStr = "";
        // Make 10 cylinders along each trajectory
        for (int i=1; i<=10; ++i) {
            //
            // Cylinder color (have some variation between Blue, Green, Red)
            //
            String cylinderColor = "Blue";
            if ((i > 7) && (i < 10)) {
                cylinderColor = "Green";
            } else if (i == 10) {
                cylinderColor = "Red";
            }

            //
            // NB: Cylinder heights are a function of their position;
            //     they get taller as we move toward increasing positive x,y
            //

            //
            // Scan cylinders along a straight forward trajectory
            //
            cyl_xpos = i * Math.sin(Math.toRadians(heading));
            cyl_ypos = i * Math.cos(Math.toRadians(heading));
            cyl_height = 0.07 * (20.0 + cyl_xpos + cyl_ypos);
            // Show the middle trajectory line rising up in altitude
            double middle_traj_alt = PLANE_ALT * Math.exp(0.05 * (i - 1));         // altI+Math.exp(0.1*i)/Math.exp(0.1);
            lineAStr = lineAStr + String.format("(%.2f,%.2f,%.2f)", cyl_xpos, middle_traj_alt, cyl_ypos);
            if (i < 10) {
                lineAStr = lineAStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/A" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, 0.0, 0.0, 0.0, "", 0.4, Arrays.asList(PlayerWorldState.LimitPrecision(i * 0.3), PlayerWorldState.LimitPrecision(cyl_height), PlayerWorldState.LimitPrecision(i * 0.3))));
            //
            // Scan cylinders along a trajectory bearing to the right
            //
            cyl_xpos = i * Math.sin(Math.toRadians(heading + ((i * 2.0) + 45.0)));
            cyl_ypos = i * Math.cos(Math.toRadians(heading + ((i * 2.0) + 45.0)));
            cyl_height = 0.07 * (20.0 + cyl_xpos + cyl_ypos);
            lineBStr = lineBStr + String.format("(%.2f,%.2f,%.2f)", cyl_xpos, PLANE_ALT, cyl_ypos);
            if (i < 10) {
                lineBStr = lineBStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/B" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, 0.0, 0.0, 0.0, "", 0.4, Arrays.asList(PlayerWorldState.LimitPrecision(i * 0.3), PlayerWorldState.LimitPrecision(cyl_height), PlayerWorldState.LimitPrecision(i * 0.3))));
            //
            // Scan cylinders along a trajectory bearing to the left
            //
            cyl_xpos = i * Math.sin(Math.toRadians(heading - ((i * 2.0) + 45.0)));
            cyl_ypos = i * Math.cos(Math.toRadians(heading - ((i * 2.0) + 45.0)));
            cyl_height = 0.07 * (20.0 + cyl_xpos + cyl_ypos);
            lineCStr = lineCStr + String.format("(%.2f,%.2f,%.2f)", cyl_xpos, PLANE_ALT, cyl_ypos);
            if (i < 10) {
                lineCStr = lineCStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/C" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, 0.0, 0.0, 0.0, "", 0.4, Arrays.asList(PlayerWorldState.LimitPrecision(i * 0.3), PlayerWorldState.LimitPrecision(cyl_height), PlayerWorldState.LimitPrecision(i * 0.3))));
        }
        //
        // Add lines displaying the 3 trajectories
        //
        objects.add(new PlayerComponentState("Base" + "/LineA", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineAStr));
        objects.add(new PlayerComponentState("Base" + "/LineB", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineBStr));
        objects.add(new PlayerComponentState("Base" + "/LineC", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineCStr));

        PlayerWorldState playerState = new PlayerWorldState(time_sec,playerName,objects);

        return playerState.toJson();
    }
}
