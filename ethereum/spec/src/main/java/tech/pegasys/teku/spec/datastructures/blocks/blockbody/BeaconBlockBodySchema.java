/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public interface BeaconBlockBodySchema<T extends BeaconBlockBody> extends SszContainerSchema<T> {
  T createBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits);

  T createEmpty();

  @Override
  T createFromBackingNode(TreeNode node);

  SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema();

  SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema();

  SszListSchema<Attestation, ?> getAttestationsSchema();

  SszListSchema<Deposit, ?> getDepositsSchema();

  SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema();
}
