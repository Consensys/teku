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

package tech.pegasys.teku.infrastructure.ssz;

import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;

public interface SszUnion extends SszData {

  int getSelector();

  SszData getValue();

  @Override
  SszUnionSchema<?> getSchema();

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  default SszMutableComposite<SszData> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
