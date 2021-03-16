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

package tech.pegasys.teku.ssz.schema.collections;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.schema.collections.impl.SszBytes32VectorSchemaImpl;

public interface SszBytes32VectorSchema<SszVectorT extends SszBytes32Vector>
    extends SszPrimitiveVectorSchema<Bytes32, SszBytes32, SszVectorT> {

  static SszBytes32VectorSchema<SszBytes32Vector> create(int length) {
    return new SszBytes32VectorSchemaImpl<>(length);
  }
}
