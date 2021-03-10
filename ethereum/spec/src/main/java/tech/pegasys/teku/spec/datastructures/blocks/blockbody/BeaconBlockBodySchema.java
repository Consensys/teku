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
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
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

public class BeaconBlockBodySchema
    extends ContainerSchema8<
        BeaconBlockBody,
        SszVector<SszByte>,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>> {

  private BeaconBlockBodySchema(
      SszSchema<SszVector<SszByte>> randaoRevealSchema,
      SszSchema<Eth1Data> eth1DataSchema,
      SszSchema<SszBytes32> graffitiSchema,
      SszSchema<SszList<ProposerSlashing>> proposerSlashingsSchema,
      SszSchema<SszList<AttesterSlashing>> attesterSlashingsSchema,
      SszSchema<SszList<Attestation>> attestationsSchema,
      SszSchema<SszList<Deposit>> depositsSchema,
      SszSchema<SszList<SignedVoluntaryExit>> voluntaryExitsSchema) {
    super(
        "BeaconBlockBody",
        namedSchema(BlockBodyFields.RANDAO_REVEAL.name(), randaoRevealSchema),
        namedSchema(BlockBodyFields.ETH1_DATA.name(), eth1DataSchema),
        namedSchema(BlockBodyFields.GRAFFITI.name(), graffitiSchema),
        namedSchema(BlockBodyFields.PROPOSER_SLASHINGS.name(), proposerSlashingsSchema),
        namedSchema(BlockBodyFields.ATTESTER_SLASHINGS.name(), attesterSlashingsSchema),
        namedSchema(BlockBodyFields.ATTESTATIONS.name(), attestationsSchema),
        namedSchema(BlockBodyFields.DEPOSITS.name(), depositsSchema),
        namedSchema(BlockBodyFields.VOLUNTARY_EXITS.name(), voluntaryExitsSchema));
  }

  public static BeaconBlockBodySchema create(final SpecConstants constants) {
    return new BeaconBlockBodySchema(
        SszComplexSchemas.BYTES_96_SCHEMA,
        Eth1Data.SSZ_SCHEMA,
        SszPrimitiveSchemas.BYTES32_SCHEMA,
        SszListSchema.createAsList(
            ProposerSlashing.SSZ_SCHEMA, constants.getMaxProposerSlashings()),
        SszListSchema.createAsList(
            AttesterSlashing.SSZ_SCHEMA, constants.getMaxAttesterSlashings()),
        SszListSchema.createAsList(Attestation.SSZ_SCHEMA, constants.getMaxAttestations()),
        SszListSchema.createAsList(Deposit.SSZ_SCHEMA, constants.getMaxDeposits()),
        SszListSchema.createAsList(
            SignedVoluntaryExit.SSZ_SCHEMA, constants.getMaxVoluntaryExits()));
  }

  @Deprecated
  public static BeaconBlockBodySchema create() {
    return new BeaconBlockBodySchema(
        SszComplexSchemas.BYTES_96_SCHEMA,
        Eth1Data.SSZ_SCHEMA,
        SszPrimitiveSchemas.BYTES32_SCHEMA,
        SszListSchema.createAsList(ProposerSlashing.SSZ_SCHEMA, Constants.MAX_PROPOSER_SLASHINGS),
        SszListSchema.createAsList(AttesterSlashing.SSZ_SCHEMA, Constants.MAX_ATTESTER_SLASHINGS),
        SszListSchema.createAsList(Attestation.SSZ_SCHEMA, Constants.MAX_ATTESTATIONS),
        SszListSchema.createAsList(Deposit.SSZ_SCHEMA, Constants.MAX_DEPOSITS),
        SszListSchema.createAsList(SignedVoluntaryExit.SSZ_SCHEMA, Constants.MAX_VOLUNTARY_EXITS));
  }

  public BeaconBlockBody createEmpty() {
    return new BeaconBlockBody(this);
  }

  public BeaconBlockBody createBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    return new BeaconBlockBody(
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
  public BeaconBlockBody createFromBackingNode(TreeNode node) {
    return new BeaconBlockBody(this, node);
  }
}
