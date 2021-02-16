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

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;

public interface SszVectorSchema<
        ElementDataT extends SszData, SszVectorT extends SszVector<ElementDataT>>
    extends SszCollectionSchema<ElementDataT, SszVectorT> {

  default int getLength() {
    long maxLength = getMaxLength();
    if (maxLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxLength);
    }
    return (int) maxLength;
  }

  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length) {
    return create(elementSchema, length, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length, SszSchemaHints hints) {
    if (elementSchema == SszPrimitiveSchemas.BIT_SCHEMA) {
      return (SszVectorSchema<ElementDataT, ? extends SszVector<ElementDataT>>)
          SszBitvectorSchema.create(length);
    } else {
      return new SszVectorSchemaImpl<>(elementSchema, length, false, hints);
    }
  }
}
