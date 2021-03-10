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
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema8;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class BeaconBlockBodySchemaPhase0
    extends ContainerSchema8<
        BeaconBlockBodyPhase0,
        SszVector<SszByte>,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>>
    implements BeaconBlockBodySchema<BeaconBlockBodyPhase0> {

  private BeaconBlockBodySchemaPhase0(
      NamedSchema<SszVector<SszByte>> randaoRevealSchema,
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

  public static BeaconBlockBodySchema<BeaconBlockBodyPhase0> create(final SpecConstants constants) {
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
        namedSchema(BlockBodyFields.RANDAO_REVEAL.name(), SszComplexSchemas.BYTES_96_SCHEMA),
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
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    return new BeaconBlockBodyPhase0(
        this,
        SszUtils.toSszByteVector(randao_reveal.toBytesCompressed()),
        eth1_data,
        new SszBytes32(graffiti),
        SszUtils.toSszList(getProposerSlashingsSchema(), proposer_slashings),
        SszUtils.toSszList(getAttesterSlashingsSchema(), attester_slashings),
        SszUtils.toSszList(getAttestationsSchema(), attestations),
        SszUtils.toSszList(getDepositsSchema(), deposits),
        SszUtils.toSszList(getVoluntaryExitsSchema(), voluntary_exits));
  }

  @Override
  public BeaconBlockBodyPhase0 createEmpty() {
    return new BeaconBlockBodyPhase0(this);
  }

  @Override
  public SszSchema<SszList<ProposerSlashing>> getFieldSchema3() {
    return super.getFieldSchema3();
  }

  SszSchema<SszList<ProposerSlashing>> getProposerSlashingsSchema() {
    return getFieldSchema3();
  }

  SszSchema<SszList<AttesterSlashing>> getAttesterSlashingsSchema() {
    return getFieldSchema4();
  }

  SszSchema<SszList<Attestation>> getAttestationsSchema() {
    return getFieldSchema5();
  }

  SszSchema<SszList<Deposit>> getDepositsSchema() {
    return getFieldSchema6();
  }

  SszSchema<SszList<SignedVoluntaryExit>> getVoluntaryExitsSchema() {
    return getFieldSchema7();
  }

  @Override
  public BeaconBlockBodyPhase0 createFromBackingNode(TreeNode node) {
    return new BeaconBlockBodyPhase0(this, node);
  }
}
