/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.fork_choice.implementatations;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class StoreBackedForkChoiceProcessor extends ForkChoiceProcessor {
  private final BlockImporter blockImporter;

  public StoreBackedForkChoiceProcessor(RecentChainData chainData) {
    super(chainData);
    blockImporter = new BlockImporter(chainData, new EventBus());
  }

  @Override
  public boolean processBlock(SignedBeaconBlock block) {
    return blockImporter.importBlock(block).isSuccessful();
  }

  @Override
  public boolean processAttestation(Attestation attestation) {
    UpdatableStore.StoreTransaction transaction = chainData.startStoreTransaction();
    AttestationProcessingResult attestationProcessingResult =
        ForkChoiceUtil.on_attestation(
            transaction, ValidateableAttestation.fromAttestation(attestation), st);
    if (attestationProcessingResult.isSuccessful()) {
      transaction.commit().join();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public Bytes32 processHead() {
    UpdatableStore.StoreTransaction transaction = chainData.startStoreTransaction();
    transaction.updateHead();
    transaction.commit().join();

    return chainData.getStore().getHead();
  }
}
