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

package net.consensys.artemis.datastructures.BeaconChainState;

import net.consensys.artemis.ethereum.core.Hash;
import net.consensys.artemis.util.uint.UInt384;
import net.consensys.artemis.util.uint.UInt64;

public class ValidatorRecord {

  private UInt384 pubkey;
  private Hash withdrawal_credentials;
  private Hash randao_commitment;
  private UInt64 randao_skips;
  public UInt64 balance;
  public UInt64 status;
  private UInt64 last_status_change_slot;
  private UInt64 exit_count;

  public ValidatorRecord() {

  }

}
