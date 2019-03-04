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

package tech.pegasys.artemis.pow.event;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;

public class Eth2Genesis extends AbstractEvent<Eth2GenesisEventResponse>
    implements Eth2GenesisEvent {

  private Bytes32 deposit_root;
  private Bytes time;

  public Eth2Genesis(Eth2GenesisEventResponse response) {
    super(response);
    deposit_root = Bytes32.leftPad(Bytes.wrap(response.deposit_root));
    time = Bytes32.leftPad(Bytes.wrap(response.time));
  }

  public Bytes32 getDeposit_root() {
    return deposit_root;
  }

  public Bytes getTime() {
    return time;
  }
}
