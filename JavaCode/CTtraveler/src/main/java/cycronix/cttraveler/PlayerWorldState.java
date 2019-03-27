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

PlayerWorldState

A class representing the objects that make up a game player's world.

John Wilson, Erigo Technologies

version: 2019-03-25

*/

package cycronix.cttraveler;

import com.google.gson.Gson;

import java.util.List;

public class PlayerWorldState {

    private String player;
    private double time = 0F;
    private String mode = "Live";
    private List<PlayerComponentState> objects;

    public PlayerWorldState(double timeI,String playerNameI,List<PlayerComponentState> objectsI) {
        player = playerNameI;
        time = timeI;
        objects = objectsI;
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

    public String toJson()
    {
        Gson gson = new Gson();
        String unityStr = gson.toJson(this);
        return unityStr;
    }

}
