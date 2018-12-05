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

import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int24;

public class ShardAndCommittee {

  private Int16 shard_id;
  private Int24[] committee;

  public ShardAndCommittee() {

  }

}
