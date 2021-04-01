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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.util.config.Constants;

public class SyncCommitteeContributionSchema
    extends ContainerSchema5<
        SyncCommitteeContribution, SszUInt64, SszBytes32, SszUInt64, SszBitvector, SszSignature> {

  private SyncCommitteeContributionSchema(
      final NamedSchema<SszUInt64> slotSchema,
      final NamedSchema<SszBytes32> beaconBlockRootSchema,
      final NamedSchema<SszUInt64> subcommitteeIndexSchema,
      final NamedSchema<SszBitvector> aggregationBitsSchema,
      final NamedSchema<SszSignature> signatureSchema) {
    super(
        "SyncCommitteeContribution",
        slotSchema,
        beaconBlockRootSchema,
        subcommitteeIndexSchema,
        aggregationBitsSchema,
        signatureSchema);
  }

  public static SyncCommitteeContributionSchema create(final SpecConfigAltair specConfig) {
    return new SyncCommitteeContributionSchema(
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("subcommittee_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "aggregation_bits",
            SszBitvectorSchema.create(
                specConfig.getSyncCommitteeSize() / Constants.SYNC_COMMITTEE_SUBNET_COUNT)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SyncCommitteeContribution createFromBackingNode(final TreeNode node) {
    return new SyncCommitteeContribution(this, node);
  }
}
