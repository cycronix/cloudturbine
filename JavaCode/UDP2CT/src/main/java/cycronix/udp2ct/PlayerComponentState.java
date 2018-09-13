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

version: 2018-09-05

*/

package cycronix.udp2ct;

import java.util.ArrayList;
import java.util.List;

public class PlayerComponentState {

    private String id;
    private String prefab;
    private boolean state;
    private List<Double> pos;
    private List<Double> rot;
    private String custom;

    public PlayerComponentState(String idI, String prefabI, boolean stateI, double xI, double altI, double yI, double pitchI, double headingI, double rollI, String urlI) {
        id = idI;
        prefab = prefabI;
        state = stateI;
        pos = new ArrayList<Double>();
        pos.add(xI);
        pos.add(altI);
        pos.add(yI);
        rot = new ArrayList<Double>();
        rot.add(pitchI);
        rot.add(headingI);
        rot.add(rollI);
        custom = urlI;
    }

}
