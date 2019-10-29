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

package org.ethereum.beacon.chain.storage;

import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.SingleValueSource;
import tech.pegasys.artemis.ethereum.core.Hash32;

public interface BeaconChainStorage {

  BeaconBlockStorage getBlockStorage();

  DataSource<Hash32, BeaconBlockHeader> getBlockHeaderStorage();

  BeaconStateStorage getStateStorage();

  BeaconTupleStorage getTupleStorage();

  SingleValueSource<Checkpoint> getJustifiedStorage();

  SingleValueSource<Checkpoint> getFinalizedStorage();

  void commit();
}
