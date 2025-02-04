/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.eip7732.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodyEip7732;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersEip7732Test {

  private final Spec spec = TestSpecFactory.createMinimalEip7732();
  private final PredicatesEip7732 predicates = new PredicatesEip7732(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsEip7732 schemaDefinitionsEip7732 =
      SchemaDefinitionsEip7732.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersEip7732 miscHelpers =
      new MiscHelpersEip7732(
          spec.getGenesisSpecConfig().toVersionEip7732().orElseThrow(),
          predicates,
          schemaDefinitionsEip7732);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void verifyBlobKzgCommitmentInclusionProofShouldValidate() {
    final UInt64 slot = UInt64.valueOf(3);
    final SignedExecutionPayloadEnvelope executionPayloadEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope();
    final SszList<SszKZGCommitment> kzgCommitments =
        executionPayloadEnvelope.getMessage().getBlobKzgCommitments();
    final ExecutionPayloadHeader header =
        schemaDefinitionsEip7732
            .getExecutionPayloadHeaderSchema()
            .createExecutionPayloadHeader(
                headerBuilder -> {
                  final ExecutionPayload payload =
                      executionPayloadEnvelope.getMessage().getPayload();
                  headerBuilder
                      .blockHash(payload.getBlockHash())
                      .gasLimit(payload.getGasLimit())
                      .parentBlockHash(dataStructureUtil::randomBytes32)
                      .parentBlockRoot(dataStructureUtil::randomBytes32)
                      .builderIndex(dataStructureUtil::randomUInt64)
                      .slot(() -> slot)
                      .value(dataStructureUtil::randomUInt64)
                      .blobKzgCommitmentsRoot(kzgCommitments::hashTreeRoot);
                });
    final int numberOfCommitments = kzgCommitments.size();
    final BeaconBlockBodyDeneb beaconBlockBody =
        BeaconBlockBodyEip7732.required(
            dataStructureUtil.randomFullBeaconBlockBody(
                slot,
                builder -> {
                  if (builder.supportsSignedExecutionPayloadHeader()) {
                    builder.signedExecutionPayloadHeader(
                        schemaDefinitionsEip7732
                            .getSignedExecutionPayloadHeaderSchema()
                            .create(header, dataStructureUtil.randomSignature()));
                  }
                }));
    final BeaconBlock beaconBlock =
        new BeaconBlock(
            schemaDefinitionsEip7732.getBeaconBlockSchema(),
            dataStructureUtil.randomSlot(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            beaconBlockBody);
    final BeaconBlockHeader blockHeader = BeaconBlockHeader.fromBlock(beaconBlock);

    // Let's check all blobSidecars
    for (int i = 0; i < numberOfCommitments; ++i) {
      final UInt64 blobSidecarIndex = UInt64.valueOf(i);
      final List<Bytes32> merkleProof =
          miscHelpers.computeKzgCommitmentInclusionProof(
              blobSidecarIndex, executionPayloadEnvelope.getMessage(), beaconBlockBody);
      assertThat(merkleProof.size()).isEqualTo(miscHelpers.getKzgCommitmentInclusionProofDepth());
      final BlobSidecar blobSidecar =
          dataStructureUtil
              .createRandomBlobSidecarBuilder()
              .signedBeaconBlockHeader(
                  new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
              .index(blobSidecarIndex)
              .kzgCommitment(kzgCommitments.get(blobSidecarIndex.intValue()).getBytes())
              .kzgCommitmentInclusionProof(merkleProof)
              .build();
      assertThat(miscHelpers.verifyBlobKzgCommitmentInclusionProof(blobSidecar)).isTrue();
      assertThat(blobSidecar.isKzgCommitmentInclusionProofValidated()).isTrue();
    }
  }
}
