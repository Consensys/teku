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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class BeaconBlockBodySchemaPhase0
    extends ContainerSchema8<
        BeaconBlockBodyPhase0,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>>
    implements BeaconBlockBodySchema<BeaconBlockBodyPhase0> {

  private BeaconBlockBodySchemaPhase0(
      NamedSchema<SszSignature> randaoRevealSchema,
      NamedSchema<Eth1Data> eth1DataSchema,
      NamedSchema<SszBytes32> graffitiSchema,
      NamedSchema<SszList<ProposerSlashing>> proposerSlashingsSchema,
      NamedSchema<SszList<AttesterSlashing>> attesterSlashingsSchema,
      NamedSchema<SszList<Attestation>> attestationsSchema,
      NamedSchema<SszList<Deposit>> depositsSchema,
      NamedSchema<SszList<SignedVoluntaryExit>> voluntaryExitsSchema) {
    super(
        "BeaconBlockBody",
        randaoRevealSchema,
        eth1DataSchema,
        graffitiSchema,
        proposerSlashingsSchema,
        attesterSlashingsSchema,
        attestationsSchema,
        depositsSchema,
        voluntaryExitsSchema);
  }

  public static BeaconBlockBodySchemaPhase0 create(final SpecConfig specConfig) {
    return create(
        specConfig.getMaxProposerSlashings(),
        specConfig.getMaxAttesterSlashings(),
        specConfig.getMaxAttestations(),
        specConfig.getMaxDeposits(),
        specConfig.getMaxVoluntaryExits());
  }

  private static BeaconBlockBodySchemaPhase0 create(
      final long maxProposerSlashings,
      final long maxAttesterSlashings,
      final long maxAttestations,
      final long maxDeposits,
      final long maxVoluntaryExits) {
    return new BeaconBlockBodySchemaPhase0(
        namedSchema(BlockBodyFields.RANDAO_REVEAL.name(), SszSignatureSchema.INSTANCE),
        namedSchema(BlockBodyFields.ETH1_DATA.name(), Eth1Data.SSZ_SCHEMA),
        namedSchema(BlockBodyFields.GRAFFITI.name(), SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(
            BlockBodyFields.PROPOSER_SLASHINGS.name(),
            SszListSchema.create(ProposerSlashing.SSZ_SCHEMA, maxProposerSlashings)),
        namedSchema(
            BlockBodyFields.ATTESTER_SLASHINGS.name(),
            SszListSchema.create(AttesterSlashing.SSZ_SCHEMA, maxAttesterSlashings)),
        namedSchema(
            BlockBodyFields.ATTESTATIONS.name(),
            SszListSchema.create(Attestation.SSZ_SCHEMA, maxAttestations)),
        namedSchema(
            BlockBodyFields.DEPOSITS.name(), SszListSchema.create(Deposit.SSZ_SCHEMA, maxDeposits)),
        namedSchema(
            BlockBodyFields.VOLUNTARY_EXITS.name(),
            SszListSchema.create(SignedVoluntaryExit.SSZ_SCHEMA, maxVoluntaryExits)));
  }

  @Override
  public BeaconBlockBody createBlockBody(final Consumer<BeaconBlockBodyBuilder> builderConsumer) {
    final BeaconBlockBodyBuilderPhase0 builder = new BeaconBlockBodyBuilderPhase0().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public BeaconBlockBodyPhase0 createEmpty() {
    return new BeaconBlockBodyPhase0(this);
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
  public BeaconBlockBodyPhase0 createFromBackingNode(TreeNode node) {
    return new BeaconBlockBodyPhase0(this, node);
  }
}
