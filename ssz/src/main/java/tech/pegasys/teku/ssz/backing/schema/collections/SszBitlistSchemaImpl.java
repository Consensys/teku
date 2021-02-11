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

package tech.pegasys.teku.ssz.backing.schema.collections;

import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.collections.SszBitlistImpl;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public class SszBitlistSchemaImpl extends AbstractSszListSchema<SszBit, SszBitlist>
    implements SszBitlistSchema<SszBitlist> {

  public SszBitlistSchemaImpl(long maxLength) {
    super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
  }

  @Override
  public SszBitlist createFromBackingNode(TreeNode node) {
    return new SszBitlistImpl(this, node);
  }

  @Override
  public SszBitlist fromLegacy(Bitlist bitlist) {
    return new SszBitlistImpl(this, bitlist);
  }

  @Override
  public SszBitlist createZero(int zeroBitsCount) {
    return fromLegacy(new Bitlist(zeroBitsCount, getMaxLength()));
  }
}
