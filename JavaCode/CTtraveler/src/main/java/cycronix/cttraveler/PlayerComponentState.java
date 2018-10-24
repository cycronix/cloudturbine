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

PlayerComponentState

A class representing one component of a game player's world.

John Wilson, Erigo Technologies

version: 2018-10-17

*/

package cycronix.cttraveler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerComponentState {

    private String id;
    private String model;
    // "state" not required (default in CTrollaball is true)
    // private boolean state = true;
    private List<Double> pos;
    private List<Double> rot;

    // Can initialize scale to be the 3-element array Arrays.asList(0.0, 0.0, 0.0),
    // which should be interpreted to mean "no change to the native scale";
    // however, scale is an optional field, so leaving it out will be interpreted
    // the same way and it doesn't "puff" out the JSON packet with unnecessary fields.
    private List<Double> scale;

    // link is an optional field
    // private String link;

    // Can initialize color to be the 4-element array Arrays.asList(0.0, 0.0, 0.0, 0.0),
    // which should be interpreted to mean "use the native object color";
    // however, color is an optional field, so leaving it out will be interpreted
    // the same way and it doesn't "puff" out the JSON packet with unnecessary fields.
    private List<Double> color;

    private String custom;

    public PlayerComponentState(String idI, String modelColorI, String modelI, boolean stateI, double xI, double altI, double yI, double pitchI, double headingI, double rollI, String urlI, double transparencyI, List<Double> scaleI, String customI) {
        this(idI, modelColorI, modelI, stateI, xI, altI, yI, pitchI, headingI, rollI, urlI, transparencyI, scaleI);
        custom = customI;
    }

    public PlayerComponentState(String idI, String modelColorI, String modelI, boolean stateI, double xI, double altI, double yI, double pitchI, double headingI, double rollI, String urlI, double transparencyI, List<Double> scaleI) {
        id = idI;
        model = modelI;
        // Just assume default state, true
        // state = stateI;
        pos = new ArrayList<Double>();
        pos.add(PlayerWorldState.LimitPrecision(xI));
        pos.add(PlayerWorldState.LimitPrecision(altI));
        pos.add(PlayerWorldState.LimitPrecision(yI));
        rot = new ArrayList<Double>();
        rot.add(PlayerWorldState.LimitPrecision(pitchI));
        rot.add(PlayerWorldState.LimitPrecision(headingI));
        rot.add(PlayerWorldState.LimitPrecision(rollI));
        // we don't currently use link/URL
        // link = urlI;
        if (scaleI != null) {
            scale = scaleI;
        }
        // Set model color
        if (modelColorI.equals("Red")) {
            color = Arrays.asList(1.0, 0.0, 0.0, transparencyI);
        } else if (modelColorI.equals("Green")) {
            color = Arrays.asList(0.0, 1.0, 0.0, transparencyI);
        } else if (modelColorI.equals("Blue")) {
            color = Arrays.asList(0.0, 0.0, 1.0, transparencyI);
        } else if (modelColorI.equals("Yellow")) {
            color = Arrays.asList(1.0, 1.0, 0.0, transparencyI);
        }
    }

}
