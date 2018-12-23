/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis;

import tech.pegasys.artemis.services.ServiceController;

public final class Artemis {

    public static void main(final String... args) {
        try {
            // Process Command Line Args

            // Detect SIGTERM
            Runtime.getRuntime().addShutdownHook(new Thread(){
                @Override
                public void run(){
                    System.out.println("Artemis is shutting down");
                    ServiceController.stopAll();
                }
            });
            // Initialize services
            ServiceController.initAll();
            // Start services
            ServiceController.startAll();
        } catch (Exception e){
            System.out.println(e.toString());
        }
    }

}
