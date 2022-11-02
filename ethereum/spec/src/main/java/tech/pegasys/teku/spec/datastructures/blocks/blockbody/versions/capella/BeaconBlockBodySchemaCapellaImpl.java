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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodySchemaCapellaImpl
    extends ContainerSchema10<
        BeaconBlockBodyCapellaImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayload>
    implements BeaconBlockBodySchemaCapella<BeaconBlockBodyCapellaImpl> {
  protected BeaconBlockBodySchemaCapellaImpl(
      final SszSchema<SszSignature> fieldSchema0,
      final SszSchema<Eth1Data> fieldSchema1,
      final SszSchema<SszBytes32> fieldSchema2,
      final SszSchema<SszList<ProposerSlashing>> fieldSchema3,
      final SszSchema<SszList<AttesterSlashing>> fieldSchema4,
      final SszSchema<SszList<Attestation>> fieldSchema5,
      final SszSchema<SszList<Deposit>> fieldSchema6,
      final SszSchema<SszList<SignedVoluntaryExit>> fieldSchema7,
      final SszSchema<SyncAggregate> fieldSchema8,
      final SszSchema<ExecutionPayload> fieldSchema9) {
    super(
        fieldSchema0,
        fieldSchema1,
        fieldSchema2,
        fieldSchema3,
        fieldSchema4,
        fieldSchema5,
        fieldSchema6,
        fieldSchema7,
        fieldSchema8,
        fieldSchema9);
  }

  @Override
  public SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> bodyBuilder) {
    return null;
  }

  @Override
  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBodyCapellaImpl(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema() {
    return (SszListSchema<ProposerSlashing, ?>) getFieldSchema3();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema() {
    return (SszListSchema<AttesterSlashing, ?>) getFieldSchema4();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Attestation, ?> getAttestationsSchema() {
    return (SszListSchema<Attestation, ?>) getFieldSchema5();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return (SszListSchema<Deposit, ?>) getFieldSchema6();
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema() {
    return (SszListSchema<SignedVoluntaryExit, ?>) getFieldSchema7();
  }

  @Override
  public SyncAggregateSchema getSyncAggregateSchema() {
    return (SyncAggregateSchema) getFieldSchema8();
  }

  @Override
  public BeaconBlockBodyCapellaImpl createFromBackingNode(TreeNode node) {
    return new BeaconBlockBodyCapellaImpl(this, node);
  }

  @Override
  public ExecutionPayloadSchema getExecutionPayloadSchema() {
    return (ExecutionPayloadSchema) getFieldSchema9();
  }

  @Override
  public Optional<BeaconBlockBodySchemaBellatrix<?>> toVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  public Optional<Long> getBlindedNodeGeneralizedIndex() {
    final long childGeneralizedIndex =
        getChildGeneralizedIndex(getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD));
    final long relativeIndex = getExecutionPayloadSchema().getBlindedNodeGeneralizedIndex();
    return Optional.of(GIndexUtil.gIdxCompose(childGeneralizedIndex, relativeIndex));
  }
}
