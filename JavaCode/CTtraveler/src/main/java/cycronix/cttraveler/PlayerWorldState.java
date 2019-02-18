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
        // Earth model as a backdrop
        //
        objects.add(new PlayerComponentState("Earth1", null, "Earth1", true, 50, -1, -40, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(400.0, 1.0, 400.0)));
        //
        // Empty base object; plane, jeep, etc. will be relative to this
        //
        objects.add(new PlayerComponentState("Base", modelColorI, "Base", true, xposI, 0.0, yposI, 0.0, 0.0, 0.0, "", 1.0, null));
        //
        // Plane
        //
        objects.add(new PlayerComponentState("Base" + "/Plane", modelColorI, modelTypeI, true, 0.0, altI, 0.0, pitch_degI, hding_degI, roll_degI, "", 1.0, Arrays.asList(0.25, 0.25, 0.25)));
        //
        // Jeep position and heading
        //
        objects.add(new PlayerComponentState("Base" + "/Jeep", modelColorI, "Jeep", true, jeep_xposI, 0.0, jeep_yposI, 0.0, jeep_hding_degI, 0.0, "", 1.0, Arrays.asList(0.1, 0.1, 0.1)));
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
            if ( (i > 7) && (i < 10) ) {
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
            cyl_xpos = i * Math.sin(Math.toRadians(hding_degI));
            cyl_ypos = i * Math.cos(Math.toRadians(hding_degI));
            cyl_height = 0.07*(20.0+cyl_xpos+cyl_ypos);
            // Show the middle trajectory line rising up in altitude
            double middle_traj_alt = altI * Math.exp(0.05*(i-1));         // altI+Math.exp(0.1*i)/Math.exp(0.1);
            lineAStr = lineAStr + String.format("(%.2f,%.2f,%.2f)",cyl_xpos,middle_traj_alt,cyl_ypos);
            if (i < 10) {
                lineAStr = lineAStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/A" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(LimitPrecision(i*0.3), LimitPrecision(cyl_height), LimitPrecision(i*0.3))));
            //
            // Scan cylinders along a trajectory bearing to the right
            //
            cyl_xpos = i * Math.sin(Math.toRadians(hding_degI + ((i*2.0) + 45.0)));
            cyl_ypos = i * Math.cos(Math.toRadians(hding_degI + ((i*2.0) + 45.0)));
            cyl_height = 0.07*(20.0+cyl_xpos+cyl_ypos);
            lineBStr = lineBStr + String.format("(%.2f,%.2f,%.2f)",cyl_xpos,altI,cyl_ypos);
            if (i < 10) {
                lineBStr = lineBStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/B" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(LimitPrecision(i*0.3), LimitPrecision(cyl_height), LimitPrecision(i*0.3))));
            //
            // Scan cylinders along a trajectory bearing to the left
            //
            cyl_xpos = i * Math.sin(Math.toRadians(hding_degI - ((i*2.0) + 45.0)));
            cyl_ypos = i * Math.cos(Math.toRadians(hding_degI - ((i*2.0) + 45.0)));
            cyl_height = 0.07*(20.0+cyl_xpos+cyl_ypos);
            lineCStr = lineCStr + String.format("(%.2f,%.2f,%.2f)",cyl_xpos,altI,cyl_ypos);
            if (i < 10) {
                lineCStr = lineCStr + ";";
            }
            objects.add(new PlayerComponentState("Base" + "/C" + Integer.toString(i), cylinderColor, "Cylinder", true, cyl_xpos, cyl_height, cyl_ypos, pitch_degI, hding_degI, roll_degI, "", 0.4, Arrays.asList(LimitPrecision(i*0.3), LimitPrecision(cyl_height), LimitPrecision(i*0.3))));
        }
        //
        // Add lines displaying the 3 trajectories
        //
        objects.add(new PlayerComponentState("Base" + "/LineA", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineAStr));
        objects.add(new PlayerComponentState("Base" + "/LineB", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineBStr));
        objects.add(new PlayerComponentState("Base" + "/LineC", "Red", "Line", true, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "", 1.0, Arrays.asList(1.0, 1.0, 1.0), lineCStr));
    }

    /// <summary>
    /// Limit the precision of a given floating point value.
    /// </summary>
    /// <param name="valI">Input floating point value.</param>
    /// <returns>The double with the desired number of decimal places of precision.</returns>
    public static double LimitPrecision(double valI)
    {
        // Desired number of decimal places of precision.
        int prec = 3;
        return ((long)(valI * Math.pow(10.0, prec))) / Math.pow(10.0, prec);
    }

}
