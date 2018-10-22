/*
Copyright 2018 Erigo Technologies

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

PlayerWorldState

A class representing the objects that make up a game player's world.

John Wilson, Erigo Technologies

version: 2018-10-19

*/

package cycronix.cttraveler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerWorldState {

    private String player;
    private double time = 0F;
    private String mode = "Live";
    private List<PlayerComponentState> objects;

    public PlayerWorldState(double timeI,double xposI,double altI,double yposI,double pitch_degI,double hding_degI,double roll_degI,String playerNameI,String modelColorI,String modelTypeI,double jeep_xposI,double jeep_yposI,double jeep_hding_degI) {
        player = playerNameI;
        time = timeI;
        objects = new ArrayList<PlayerComponentState>();
        //
        // Plane
        //
        objects.add(new PlayerComponentState(player, modelColorI, modelTypeI, true, xposI, altI, yposI, pitch_degI, hding_degI, roll_degI, "", 1.0, null));
        //
        // Jeep position and heading
        //
        // double jeep_xpos = xposI - 3 * Math.sin(Math.toRadians(hding_degI));
        // double jeep_ypos = yposI - 3 * Math.cos(Math.toRadians(hding_degI));
        objects.add(new PlayerComponentState(player + ".Jeep", modelColorI, "Jeep", true, jeep_xposI, 0.0, jeep_yposI, 0.0, jeep_hding_degI, 0.0, "", 1.0, null));
        //
        // Add scan cylinders along 3 trajectories
        //
        double cyl_xpos;
        double cyl_ypos;
        // To add some variation, set the cylinder height based on the plane's current x,y position
        double cyl_height = 1.0 + 0.1*(20.0+(double)(xposI+yposI));
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
            if ( (i > 7) && (i < 10) ) {
                cylinderColor = "Green";
            } else if (i == 10) {
                cylinderColor = "Red";
            }
            //
            // Scan cylinders along a straight forward trajectory
            //
            cyl_xpos = xposI + i * Math.sin(Math.toRadians(hding_degI));
            cyl_ypos = yposI + i * Math.cos(Math.toRadians(hding_degI));
            lineAStr = lineAStr + String.format("(%.2f,5,%.2f)",cyl_xpos,cyl_ypos);
            if (i < 10) {
                lineAStr = lineAStr + ";";
            }
            objects.add(new PlayerComponentState(player + ".A" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(i*0.3, cyl_height, i*0.3)));
            //
            // Scan cylinders along a trajectory bearing to the right
            //
            cyl_xpos = xposI + i * Math.sin(Math.toRadians(hding_degI + ((i*2.0) + 45.0)));
            cyl_ypos = yposI + i * Math.cos(Math.toRadians(hding_degI + ((i*2.0) + 45.0)));
            lineBStr = lineBStr + String.format("(%.2f,5,%.2f)",cyl_xpos,cyl_ypos);
            if (i < 10) {
                lineBStr = lineBStr + ";";
            }
            objects.add(new PlayerComponentState(player + ".B" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(i*0.3, cyl_height, i*0.3)));
            //
            // Scan cylinders along a trajectory bearing to the left
            //
            cyl_xpos = xposI + i * Math.sin(Math.toRadians(hding_degI - ((i*2.0) + 45.0)));
            cyl_ypos = yposI + i * Math.cos(Math.toRadians(hding_degI - ((i*2.0) + 45.0)));
            lineCStr = lineCStr + String.format("(%.2f,5,%.2f)",cyl_xpos,cyl_ypos);
            if (i < 10) {
                lineCStr = lineCStr + ";";
            }
            objects.add(new PlayerComponentState(player + ".C" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(i*0.3, cyl_height, i*0.3)));
        }
        //
        // Add lines displaying the 3 trajectories
        //
        objects.add(new PlayerComponentState(player + ".LineA", "Red", "Line", true, xposI, 0, yposI, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineAStr));
        objects.add(new PlayerComponentState(player + ".LineB", "Red", "Line", true, xposI, 0, yposI, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineBStr));
        objects.add(new PlayerComponentState(player + ".LineC", "Red", "Line", true, xposI, 0, yposI, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineCStr));
    }

}
