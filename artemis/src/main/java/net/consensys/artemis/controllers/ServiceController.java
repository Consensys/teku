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

package net.consensys.artemis.controllers;

import net.consensys.artemis.services.BeaconChainService;
import net.consensys.artemis.services.PowchainService;
import net.consensys.artemis.services.ServiceFactory;


public class ServiceController {
    private static final BeaconChainService beaconChainService = ServiceFactory.getInstance(BeaconChainService.class).getInstance();;
    private static final PowchainService powchainService = ServiceFactory.getInstance(PowchainService.class).getInstance();


    // initialize/register all services
    public static void init(){

        beaconChainService.init();
        powchainService.init();

        // Validator Service

        // P2P Service

        // RPC Service
    }

    public static void start(){
        // start all services
        beaconChainService.start();
        powchainService.start();

    }

    public static void stop(){
        // stop all services
        beaconChainService.stop();
        powchainService.stop();
    }
}
