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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszPrimitiveVectorSchemaImpl;

public interface SszPrimitiveVectorSchema<
        ElementT,
        SszElementT extends SszPrimitive<ElementT, SszElementT>,
        SszVectorT extends SszPrimitiveVector<ElementT, SszElementT>>
    extends SszPrimitiveCollectionSchema<ElementT, SszElementT, SszVectorT>,
        SszVectorSchema<SszElementT, SszVectorT> {

  static <ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
      SszPrimitiveVectorSchema<ElementT, SszElementT, ?> create(
          SszPrimitiveSchema<ElementT, SszElementT> elementSchema, int length) {
    return create(elementSchema, length, SszSchemaHints.none());
  }

  @SuppressWarnings("unchecked")
  static <PrimT, SszPrimT extends SszPrimitive<PrimT, SszPrimT>>
      SszPrimitiveVectorSchema<PrimT, SszPrimT, ?> create(
          SszPrimitiveSchema<PrimT, SszPrimT> elementSchema, long length, SszSchemaHints hints) {
    if (elementSchema == SszPrimitiveSchemas.BIT_SCHEMA) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>) SszBitvectorSchema.create(length);
    } else if (elementSchema == SszPrimitiveSchemas.BYTE_SCHEMA) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>)
          SszByteVectorSchema.create((int) length);
    } else if (elementSchema == SszPrimitiveSchemas.BYTES32_SCHEMA) {
      return (SszPrimitiveVectorSchema<PrimT, SszPrimT, ?>)
          SszBytes32VectorSchema.create((int) length);
    } else {
      return new SszPrimitiveVectorSchemaImpl<>(elementSchema, length);
    }
  }
}
