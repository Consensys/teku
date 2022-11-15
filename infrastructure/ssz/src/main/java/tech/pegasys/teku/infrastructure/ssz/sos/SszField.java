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

package tech.pegasys.teku.infrastructure.ssz.sos;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public class SszField {
  private final int index;
  private final String name;
  private final Supplier<SszSchema<?>> viewType;
  private final boolean overridesOtherField;

  public SszField(int index, SszSchema<?> sszSchema) {
    this(index, () -> sszSchema);
  }

  public SszField(int index, Supplier<SszSchema<?>> viewType) {
    this(index, "field-" + index, viewType, false);
  }

  public SszField(int index, SszFieldName name, SszSchema<?> sszSchema) {
    this(index, name.getSszFieldName(), sszSchema);
  }

  public SszField(int index, String name, SszSchema<?> sszSchema) {
    this(index, name, () -> sszSchema, false);
  }

  public SszField(int index, SszFieldName name, Supplier<SszSchema<?>> viewType) {
    this(index, name.getSszFieldName(), viewType, false);
  }

  public static SszField createOverrideField(
      int index, SszFieldName name, Supplier<SszSchema<?>> viewType) {
    return new SszField(index, name.getSszFieldName(), viewType, true);
  }

  public SszField(
      int index, String name, Supplier<SszSchema<?>> viewType, boolean overridesOtherField) {
    this.index = index;
    this.name = name;
    this.viewType = viewType;
    this.overridesOtherField = overridesOtherField;
  }

  public int getIndex() {
    return index;
  }

  public String getName() {
    return name;
  }

  public Supplier<SszSchema<?>> getSchema() {
    return viewType;
  }

  public boolean isOverridesOtherField() {
    return overridesOtherField;
  }
}
