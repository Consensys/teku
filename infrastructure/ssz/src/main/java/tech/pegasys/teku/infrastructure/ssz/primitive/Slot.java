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

package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszUInt64Schema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Slot extends SszUInt64 {

  public static class SlotSchema extends AbstractSszUInt64Schema<Slot> {

    @Override
    public Slot boxed(UInt64 rawValue) {
      return new Slot(rawValue);
    }
  }

  public Slot(UInt64 val) {
    super(val);
  }

  @Override
  public SlotSchema getSchema() {
    return (SlotSchema) super.getSchema();
  }
}
