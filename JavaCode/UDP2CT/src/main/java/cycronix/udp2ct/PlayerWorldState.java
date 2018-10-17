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

version: 2018-09-05

*/

package cycronix.udp2ct;

import java.util.ArrayList;
import java.util.List;

public class PlayerWorldState {

    private String player;
    private double time = 0F;
    private String mode = "Live";
    private List<PlayerComponentState> objects;

    public PlayerWorldState(double timeI,float xposI,float altI,float yposI,float pitch_degI,float hding_degI,float roll_degI,String modelColorI,String modelTypeI) {
        player = modelColorI;
        time = timeI;
        objects = new ArrayList<PlayerComponentState>();
        objects.add(new PlayerComponentState(modelColorI, modelTypeI, true, xposI, altI, yposI, pitch_degI, hding_degI, roll_degI, ""));
        if (false) {
            // Pickups, chart, video objects
            objects.add(new PlayerComponentState(modelColorI + ".Pickup0", "Pickup", true, 9.1000, 1.4000, -8.5000, 334.0730, 24.0876, 224.0097, ""));
            objects.add(new PlayerComponentState(modelColorI + ".Pickup1", "Pickup", true, 1.6000, 2.9000, 4.2000, 334.0730, 24.0876, 224.0097, ""));
            objects.add(new PlayerComponentState(modelColorI + ".Pickup2", "Pickup", true, -3.3000, 4.1000, -4.3000, 334.0730, 24.0876, 224.0097, ""));
            objects.add(new PlayerComponentState(modelColorI + ".Pickup3", "Pickup", true, -4.3000, 3.4000, 1.0000, 334.0730, 24.0876, 224.0097, ""));
            objects.add(new PlayerComponentState(modelColorI + ".Pickup4", "Pickup", true, 8.9000, 1.9000, -4.3000, 334.0730, 24.0876, 224.0097, ""));
            objects.add(new PlayerComponentState(modelColorI + "/CTchart", "CTchart", true, -1.2000, 2.0000, 0.0000, 0.0000, 180.0000, 0.0000, new String("http://localhost:8000/CT/CTmousetrack/x?f=d&t=" + Double.toString(timeI) + "&d=1,http://localhost:8000/CT/CTmousetrack/y?f=d&t=" + Double.toString(timeI) + "&d=1")));
            objects.add(new PlayerComponentState(modelColorI + "/CTvideo", "CTvideo", true, 1.2000, 2.0000, 0.0000, 0.0000, 180.0000, 0.0000, new String("http://localhost:8000/CT/CTstream/webcam.jpg?f=d&t=" + Double.toString(timeI))));
        }
    }

}
