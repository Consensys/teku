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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyBuilderCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadEip4844Impl;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderEip4844Impl;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyBuilderEip4844 extends BeaconBlockBodyBuilderCapella {

  private BeaconBlockBodySchemaEip4844Impl schema;
  private BlindedBeaconBlockBodySchemaEip4844Impl blindedSchema;
  private SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments;

  public BeaconBlockBodyBuilderEip4844 schema(final BeaconBlockBodySchemaEip4844Impl schema) {
    this.schema = schema;
    this.blinded = Optional.of(false);
    return this;
  }

  public BeaconBlockBodyBuilderEip4844 blindedSchema(
      final BlindedBeaconBlockBodySchemaEip4844Impl blindedSchema) {
    this.blindedSchema = blindedSchema;
    this.blinded = Optional.of(true);
    return this;
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
  protected void validateSchema() {
    checkState(schema != null || blindedSchema != null, "schema or blindedSchema must be set");
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blobKzgCommitments, "blobKzgCommitments must be specified");
  }

  @Override
  public Boolean isBlinded() {
    return blinded.orElseThrow(
        () ->
            new IllegalStateException(
                "schema or blindedSchema must be set before interacting with the builder"));
  }

  @Override
  public SafeFuture<BeaconBlockBody> build() {
    validate();
    if (isBlinded()) {
      return executionPayloadHeader
          .thenCompose(
              header -> blobKzgCommitments.thenApply(commitments -> Pair.of(header, commitments)))
          .thenApply(
              headerWithCommitments ->
                  new BlindedBeaconBlockBodyEip4844Impl(
                      blindedSchema,
                      new SszSignature(randaoReveal),
                      eth1Data,
                      SszBytes32.of(graffiti),
                      proposerSlashings,
                      attesterSlashings,
                      attestations,
                      deposits,
                      voluntaryExits,
                      syncAggregate,
                      (ExecutionPayloadHeaderEip4844Impl)
                          headerWithCommitments.getLeft().toVersionEip4844().orElseThrow(),
                      getBlsToExecutionChanges(),
                      headerWithCommitments.getRight()));
    }
    return executionPayload
        .thenCompose(
            payload -> blobKzgCommitments.thenApply(commitments -> Pair.of(payload, commitments)))
        .thenApply(
            payloadWithCommitments ->
                new BeaconBlockBodyEip4844Impl(
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
                    (ExecutionPayloadEip4844Impl)
                        payloadWithCommitments.getLeft().toVersionEip4844().orElseThrow(),
                    getBlsToExecutionChanges(),
                    payloadWithCommitments.getRight()));
  }
}
