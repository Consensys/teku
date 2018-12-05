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

package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Hash;
import net.consensys.beaconchain.util.uint.UInt384;
import net.consensys.beaconchain.util.uint.UInt64;

public class BeaconBlock {

  public UInt64 slot;
  private Hash randao_reveal;
  private Hash candidate_pow_receipt_root;
  private Hash[] ancestor_hashes;
  private Hash state_root;
  private AttestationRecord[] attestations;
  private SpecialRecord[] specials;
  private UInt384[] proposer_signature;

  public BeaconBlock() {

  }

}
