/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszPrimitiveListSchemaImpl;

public interface SszPrimitiveListSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT, SszElementT>,
        SszListT extends SszPrimitiveList<ElementT, SszElementT>>
    extends SszListSchema<SszElementT, SszListT>,
        SszPrimitiveCollectionSchema<ElementT, SszElementT, SszListT> {

  static <ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
      SszPrimitiveListSchema<ElementT, SszElementT, ?> create(
          SszPrimitiveSchema<ElementT, SszElementT> elementSchema, int maxLength) {
    return create(elementSchema, maxLength, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <PrimT, SszPrimT extends SszPrimitive<PrimT, SszPrimT>>
      SszPrimitiveListSchema<PrimT, SszPrimT, ?> create(
          SszPrimitiveSchema<PrimT, SszPrimT> elementSchema, long maxLength, SszSchemaHints hints) {
    if (elementSchema.equals(SszPrimitiveSchemas.BIT_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszBitlistSchema.create(maxLength);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT64_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszUInt64ListSchema.create(maxLength);
    } else if (elementSchema.equals(SszPrimitiveSchemas.BYTE_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszByteListSchema.create(maxLength);
    } else if (elementSchema.equals(SszPrimitiveSchemas.UINT8_SCHEMA)) {
      return (SszPrimitiveListSchema<PrimT, SszPrimT, ?>) SszByteListSchema.createUInt8(maxLength);
    } else {
      return new SszPrimitiveListSchemaImpl<>(elementSchema, maxLength);
    }
  }
}
