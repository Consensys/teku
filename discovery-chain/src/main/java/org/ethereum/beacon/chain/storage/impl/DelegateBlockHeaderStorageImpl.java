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

package org.ethereum.beacon.chain.storage.impl;

import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.db.source.DataSource;
import tech.pegasys.artemis.ethereum.core.Hash32;

public class DelegateBlockHeaderStorageImpl implements DataSource<Hash32, BeaconBlockHeader> {

  private final BeaconBlockStorage delegateBlockStorage;
  private final ObjectHasher<Hash32> objectHasher;

  public DelegateBlockHeaderStorageImpl(
      BeaconBlockStorage delegateBlockStorage, ObjectHasher<Hash32> objectHasher) {
    this.delegateBlockStorage = delegateBlockStorage;
    this.objectHasher = objectHasher;
  }

  @Override
  public Optional<BeaconBlockHeader> get(@Nonnull Hash32 key) {
    return delegateBlockStorage.get(key).map(this::createHeader);
  }

  private BeaconBlockHeader createHeader(BeaconBlock block) {
    return new BeaconBlockHeader(
        block.getSlot(),
        block.getParentRoot(),
        block.getStateRoot(),
        objectHasher.getHash(block.getBody()),
        block.getSignature());
  }

  @Override
  public void put(@Nonnull Hash32 key, @Nonnull BeaconBlockHeader value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(@Nonnull Hash32 key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void flush() {
    throw new UnsupportedOperationException();
  }
}
