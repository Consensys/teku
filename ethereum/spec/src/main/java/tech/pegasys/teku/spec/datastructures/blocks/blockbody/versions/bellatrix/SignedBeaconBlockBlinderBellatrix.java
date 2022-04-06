/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SignedBeaconBlockBlinderBellatrix extends AbstractSignedBeaconBlockBlinder {
  public SignedBeaconBlockBlinderBellatrix(SchemaDefinitions schemaDefinitions) {
    super(schemaDefinitions);
  }

  @Override
  public SignedBeaconBlock blind(SignedBeaconBlock signedUnblindedBock) {
    final BeaconBlockBodyBellatrixImpl unblindedBlockBody =
        BeaconBlockBodyBellatrixImpl.required(signedUnblindedBock.getMessage().getBody());

    final BlindedBeaconBlockBodySchemaBellatrixImpl schema =
        (BlindedBeaconBlockBodySchemaBellatrixImpl)
            schemaDefinitions.getBlindedBeaconBlockBodySchema();

    final BlindedBeaconBlockBodyBellatrixImpl blindedBody =
        new BlindedBeaconBlockBodyBellatrixImpl(
            schema,
            new SszSignature(unblindedBlockBody.getRandaoReveal()),
            unblindedBlockBody.getEth1Data(),
            SszBytes32.of(unblindedBlockBody.getGraffiti()),
            unblindedBlockBody.getProposerSlashings(),
            unblindedBlockBody.getAttesterSlashings(),
            unblindedBlockBody.getAttestations(),
            unblindedBlockBody.getDeposits(),
            unblindedBlockBody.getVoluntaryExits(),
            unblindedBlockBody.getSyncAggregate(),
            schema
                .getExecutionPayloadHeaderSchema()
                .createFromExecutionPayload(unblindedBlockBody.getExecutionPayload()));

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
