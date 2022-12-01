/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;

public class SignedBeaconBlockBlinderEip4844 extends AbstractSignedBeaconBlockBlinder {

  public SignedBeaconBlockBlinderEip4844(final SchemaDefinitionsEip4844 schemaDefinitions) {
    super(schemaDefinitions);
  }

  @Override
  public SignedBeaconBlock blind(final SignedBeaconBlock signedUnblindedBock) {
    final BeaconBlockBodyEip4844Impl unblindedBlockBody =
        BeaconBlockBodyEip4844Impl.required(signedUnblindedBock.getMessage().getBody());

    final BlindedBeaconBlockBodySchemaEip4844Impl schema =
        (BlindedBeaconBlockBodySchemaEip4844Impl)
            schemaDefinitions.getBlindedBeaconBlockBodySchema();

    final BlindedBeaconBlockBodyEip4844Impl blindedBody =
        new BlindedBeaconBlockBodyEip4844Impl(
            schema,
            unblindedBlockBody.getRandaoRevealSsz(),
            unblindedBlockBody.getEth1Data(),
            unblindedBlockBody.getGraffitiSsz(),
            unblindedBlockBody.getProposerSlashings(),
            unblindedBlockBody.getAttesterSlashings(),
            unblindedBlockBody.getAttestations(),
            unblindedBlockBody.getDeposits(),
            unblindedBlockBody.getVoluntaryExits(),
            unblindedBlockBody.getSyncAggregate(),
            schema
                .getExecutionPayloadHeaderSchema()
                .createFromExecutionPayload(unblindedBlockBody.getExecutionPayload()),
            unblindedBlockBody.getBlsToExecutionChanges(),
            unblindedBlockBody.getBlobKzgCommitments());

    final BeaconBlock blindedBeaconBlock =
        schemaDefinitions
            .getBlindedBeaconBlockSchema()
            .create(
                signedUnblindedBock.getSlot(),
                signedUnblindedBock.getProposerIndex(),
                signedUnblindedBock.getParentRoot(),
                signedUnblindedBock.getStateRoot(),
                blindedBody);

    checkState(
        blindedBeaconBlock.hashTreeRoot().equals(signedUnblindedBock.getMessage().hashTreeRoot()),
        "blinded block root do not match original unblinded block root");

    return schemaDefinitions
        .getSignedBlindedBeaconBlockSchema()
        .create(blindedBeaconBlock, signedUnblindedBock.getSignature());
  }
}
