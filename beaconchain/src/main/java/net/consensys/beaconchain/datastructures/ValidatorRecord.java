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

import net.consensys.beaconchain.ethereum.core.Address;
import net.consensys.beaconchain.ethereum.core.Hash;

import org.web3j.abi.datatypes.generated.Int128;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Int64;
import org.web3j.abi.datatypes.generated.Int8;

public class ValidatorRecord {

  private Address withdrawal_address;
  private Hash randao_commitment;
  private Int8 status;
  private Int16 withdrawal_shard;
  private Int64 exit_slot;
  private Int128 balance;
  private Int256 pubkey;

  public ValidatorRecord() {

  }

}
