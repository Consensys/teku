/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyBuilderCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDenebImpl;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderDenebImpl;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderDeneb extends BeaconBlockBodyBuilderCapella {

  private SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments;

  public BeaconBlockBodyBuilderDeneb(
      final BeaconBlockBodySchema<? extends BeaconBlockBodyDeneb> schema,
      final BeaconBlockBodySchema<? extends BlindedBeaconBlockBodyDeneb> blindedSchema) {
    super(schema, blindedSchema);
  }

  @Override
  public Boolean supportsKzgCommitments() {
    return true;
  }

  @Override
  public BeaconBlockBodyBuilder blobKzgCommitments(
      final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments) {
    this.blobKzgCommitments = blobKzgCommitments;
    return this;
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blobKzgCommitments, "blobKzgCommitments must be specified");
  }

  @Override
  public SafeFuture<BeaconBlockBody> build() {
    validate();
    if (isBlinded()) {
      final BlindedBeaconBlockBodySchemaDenebImpl schema =
          getAndValidateSchema(true, BlindedBeaconBlockBodySchemaDenebImpl.class);
      return executionPayloadHeader
          .thenCompose(
              header -> blobKzgCommitments.thenApply(commitments -> Pair.of(header, commitments)))
          .thenApply(
              headerWithCommitments ->
                  new BlindedBeaconBlockBodyDenebImpl(
                      schema,
                      new SszSignature(randaoReveal),
                      eth1Data,
                      SszBytes32.of(graffiti),
                      proposerSlashings,
                      attesterSlashings,
                      attestations,
                      deposits,
                      voluntaryExits,
                      syncAggregate,
                      (ExecutionPayloadHeaderDenebImpl)
                          headerWithCommitments.getLeft().toVersionDeneb().orElseThrow(),
                      getBlsToExecutionChanges(),
                      headerWithCommitments.getRight()));
    }

    final BeaconBlockBodySchemaDenebImpl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaDenebImpl.class);
    return executionPayload
        .thenCompose(
            payload -> blobKzgCommitments.thenApply(commitments -> Pair.of(payload, commitments)))
        .thenApply(
            payloadWithCommitments ->
                new BeaconBlockBodyDenebImpl(
                    schema,
                    new SszSignature(randaoReveal),
                    eth1Data,
                    SszBytes32.of(graffiti),
                    proposerSlashings,
                    attesterSlashings,
                    attestations,
                    deposits,
                    voluntaryExits,
                    syncAggregate,
                    (ExecutionPayloadDenebImpl)
                        payloadWithCommitments.getLeft().toVersionDeneb().orElseThrow(),
                    getBlsToExecutionChanges(),
                    payloadWithCommitments.getRight()));
  }
}
