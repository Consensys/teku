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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public abstract class AbstractBeaconStateSchema<
        T extends BeaconState, TMutable extends MutableBeaconState>
    extends AbstractSszContainerSchema<T> implements BeaconStateSchema<T, TMutable> {
  protected AbstractBeaconStateSchema(final String name, final List<SszField> allFields) {
    super(
        name,
        allFields.stream()
            .map(f -> namedSchema(f.getName(), f.getSchema().get()))
            .collect(Collectors.toList()));
    validateFields(allFields);
  }

  protected AbstractBeaconStateSchema(
      final String name, final List<SszField> uniqueFields, final SpecConfig specConfig) {
    this(name, combineFields(BeaconStateFields.getCommonFields(specConfig), uniqueFields));
  }

  private static List<SszField> combineFields(List<SszField> fieldsA, List<SszField> fieldsB) {
    List<SszField> list =
        Stream.concat(fieldsA.stream(), fieldsB.stream())
            .sorted(
                Comparator.comparing(SszField::getIndex)
                    .thenComparing(SszField::isOverridesOtherField))
            .collect(Collectors.toList());
    List<SszField> result = new ArrayList<>();
    Optional<SszField> previous = Optional.empty();
    for (SszField sszField : list) {
      if (previous.isPresent()) {
        if (sszField.isOverridesOtherField()) {
          if (!previous.get().getName().equals(sszField.getName())
              || previous.get().getIndex() != sszField.getIndex()) {
            throw new RuntimeException(
                String.format(
                    "Field override configured by field %s but replacement %s doesn't look like a correct override",
                    sszField, previous.get()));
          }
        } else {
          result.add(previous.get());
        }
      }
      previous = Optional.of(sszField);
    }
    result.add(previous.orElseThrow());

    return result;
  }

  private void validateFields(final List<SszField> fields) {
    for (int i = 0; i < fields.size(); i++) {
      final int fieldIndex = fields.get(i).getIndex();
      checkArgument(
          fieldIndex == i,
          "BeaconStateSchema fields must be ordered and contiguous.  Encountered unexpected index %s at fields element %s",
          fieldIndex,
          i);
    }

    final List<SszField> invariantFields = BeaconStateInvariants.getInvariantFields();
    checkArgument(
        fields.size() >= invariantFields.size(),
        "Must provide at least %s fields",
        invariantFields.size());
    for (SszField invariantField : invariantFields) {
      final int invariantIndex = invariantField.getIndex();
      final SszField actualField = fields.get(invariantIndex);
      checkArgument(
          actualField.equals(invariantField),
          "Expected invariant field '%s' at index %s, but got '%s'",
          invariantField.getName(),
          invariantIndex,
          actualField.getName());
    }
  }
}
