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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class Exit {

    private UInt64 slot;
    private UInt64 validator_index;
    private UInt384[] signature;

    public Exit(UInt64 slot, UInt64 validator_index, UInt384[] signature) {
        this.slot = slot;
        this.validator_index = validator_index;
        this.signature = signature;
    }

    public UInt64 getSlot() {
        return slot;
    }

    public void setSlot(UInt64 slot) {
        this.slot = slot;
    }

    public UInt64 getValidator_index() {
        return validator_index;
    }

    public void setValidator_index(UInt64 validator_index) {
        this.validator_index = validator_index;
    }

    public UInt384[] getSignature() {
        return signature;
    }

    public void setSignature(UInt384[] signature) {
        this.signature = signature;
    }
}
