/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition.events;

import org.apache.tuweni.bytes.Bytes32;

public class BroadcastAttestationEvent {

  Bytes32 headBlockRoot;

  public BroadcastAttestationEvent(Bytes32 headBlockRoot) {
    this.headBlockRoot = headBlockRoot;
  }

  public Bytes32 getHeadBlockRoot() {
    return this.headBlockRoot;
  }
}
