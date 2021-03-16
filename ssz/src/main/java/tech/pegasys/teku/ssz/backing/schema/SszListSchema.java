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

package tech.pegasys.teku.ssz.backing.schema;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.backing.schema.impl.SszListSchemaImpl;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil;

public interface SszListSchema<ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
    extends SszCollectionSchema<ElementDataT, SszListT> {

  // we need additional depth level for the length node
  long MAX_LIST_MAX_LENGTH = 1L << (GIndexUtil.MAX_DEPTH - 2);

  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> create(
          SszSchema<ElementDataT> elementSchema, long maxLength) {
    return create(elementSchema, maxLength, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <ElementDataT extends SszData>
      SszListSchema<ElementDataT, ? extends SszList<ElementDataT>> create(
          SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
    checkArgument(maxLength >= 0 && maxLength <= MAX_LIST_MAX_LENGTH);
    if (elementSchema instanceof SszPrimitiveSchema) {
      return (SszListSchema<ElementDataT, ?>)
          SszPrimitiveListSchema.create((SszPrimitiveSchema<?, ?>) elementSchema, maxLength, hints);
    } else {
      return new SszListSchemaImpl<>(elementSchema, maxLength, hints);
    }
  }
}
