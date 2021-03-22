/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state;

import java.util.List;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.containers.Container2;
import tech.pegasys.teku.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SyncCommittee
    extends Container2<SyncCommittee, SszVector<SszPublicKey>, SszVector<SszPublicKey>> {

  public static class SyncCommitteeSchema
      extends ContainerSchema2<SyncCommittee, SszVector<SszPublicKey>, SszVector<SszPublicKey>> {

    public SyncCommitteeSchema(final SpecConfigAltair specConfigAltair) {
      super(
          "SyncCommittee",
          namedSchema(
              "pubkeys",
              SszVectorSchema.create(
                  SszPublicKeySchema.INSTANCE, specConfigAltair.getSyncCommitteeSize())),
          namedSchema(
              "pubkey_aggregates",
              SszVectorSchema.create(
                  SszPublicKeySchema.INSTANCE,
                  specConfigAltair.getSyncCommitteeSize()
                      / specConfigAltair.getSyncSubcommitteeSize())));
    }

    @Override
    public SyncCommittee createFromBackingNode(TreeNode node) {
      return new SyncCommittee(this, node);
    }

    public SyncCommittee create(
        final List<SszPublicKey> pubkeys, final List<SszPublicKey> pubkeyAggregates) {
      return create(
          getPubkeysSchema().createFromElements(pubkeys),
          getPubkeyAggregatesSchema().createFromElements(pubkeyAggregates));
    }

    public SyncCommittee create(
        final SszVector<SszPublicKey> pubkeys, final SszVector<SszPublicKey> pubkeyAggregates) {
      return new SyncCommittee(this, pubkeys, pubkeyAggregates);
    }

    @SuppressWarnings("unchecked")
    public SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>> getPubkeysSchema() {
      return (SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>>) getChildSchema(0);
    }

    @SuppressWarnings("unchecked")
    public SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>> getPubkeyAggregatesSchema() {
      return (SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>>) getChildSchema(1);
    }
  }

  private SyncCommittee(final SyncCommitteeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private SyncCommittee(
      final SyncCommitteeSchema type,
      final SszVector<SszPublicKey> pubkeys,
      final SszVector<SszPublicKey> pubkeyAggregates) {
    super(type, pubkeys, pubkeyAggregates);
  }

  public SszVector<SszPublicKey> getPubkeys() {
    return getField0();
  }

  public SszVector<SszPublicKey> getPubkeyAggregates() {
    return getField1();
  }
}
