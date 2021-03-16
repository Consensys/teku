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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

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

  // TODO(#3648) - remove this field (version schemas that depend on this field through
  // SchemaDefinitions)
  @Deprecated
  public static final SpecDependent<BeaconBlockBodySchemaPhase0> SSZ_SCHEMA =
      SpecDependent.of(BeaconBlockBodySchemaPhase0::create);

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

  public static BeaconBlockBodySchemaPhase0 create(final SpecConstants constants) {
    return create(
        constants.getMaxProposerSlashings(),
        constants.getMaxAttesterSlashings(),
        constants.getMaxAttestations(),
        constants.getMaxDeposits(),
        constants.getMaxVoluntaryExits());
  }

  @Deprecated
  public static BeaconBlockBodySchemaPhase0 create() {
    return create(
        Constants.MAX_PROPOSER_SLASHINGS,
        Constants.MAX_ATTESTER_SLASHINGS,
        Constants.MAX_ATTESTATIONS,
        Constants.MAX_DEPOSITS,
        Constants.MAX_VOLUNTARY_EXITS);
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
  public BeaconBlockBodyPhase0 createBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits) {
    return new BeaconBlockBodyPhase0(
        this,
        new SszSignature(randao_reveal),
        eth1_data,
        SszBytes32.of(graffiti),
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
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
