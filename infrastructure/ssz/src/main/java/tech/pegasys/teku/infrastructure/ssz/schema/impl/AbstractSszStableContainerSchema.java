/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszStableContainerTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractSszStableContainerSchema<C extends SszStableContainer>
    extends AbstractSszContainerWithOptionalSchema<C> implements SszStableContainerSchema<C> {

  private final DeserializableTypeDefinition<C> jsonTypeDefinition;


  public AbstractSszStableContainerSchema(
      final String name,
      final List<NamedSchema<?>> definedChildrenSchemas,
      final int maxFieldCount) {
    super(name, definedChildrenSchemas, Set.of(), IntSet.of(IntStream.range(0, definedChildrenSchemas.size()).toArray()), maxFieldCount);

    this.jsonTypeDefinition = SszStableContainerTypeDefinition.createFor(this);
  }

  @Override
  int sszSerializeActiveFields(final TreeNode node, final SszWriter writer) {
    return getActiveFieldsSchema().sszSerializeTree(node,writer);
  }

  @Override
  SszBitvector sszDeserializeActiveFieldsTree(final SszReader reader) {
    final SszReader activeFieldsReader = reader.slice(getActiveFieldsSchema().getSszFixedPartSize());
    return getActiveFieldsSchema().sszDeserialize(activeFieldsReader);
  }

  @Override
  public DeserializableTypeDefinition<C> getJsonTypeDefinition() {
    return jsonTypeDefinition;
  }


  @Override
  public SszBitvector getActiveFieldsBitvectorFromBackingNode(final TreeNode node) {
    return super.getActiveFieldsBitvector(node);
  }



  @Override
  public SszBitvector getDefaultActiveFields() {
    return null;
  }

}
