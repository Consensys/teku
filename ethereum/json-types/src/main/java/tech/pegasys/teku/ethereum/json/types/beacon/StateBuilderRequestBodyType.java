/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class StateBuilderRequestBodyType {

  public static final DeserializableTypeDefinition<StateBuilderRequestBodyType>
      STATE_BUILDER_REQUEST_TYPE =
          DeserializableTypeDefinition.object(StateBuilderRequestBodyType.class)
              .name("PostStateBuildersRequestBody")
              .initializer(StateBuilderRequestBodyType::new)
              .withOptionalField(
                  "ids",
                  DeserializableTypeDefinition.listOf(STRING_TYPE),
                  StateBuilderRequestBodyType::getMaybeIds,
                  StateBuilderRequestBodyType::setIds)
              .withOptionalField(
                  "statuses",
                  DeserializableTypeDefinition.listOf(RAW_INTEGER_TYPE),
                  StateBuilderRequestBodyType::getMaybeStatuses,
                  StateBuilderRequestBodyType::setStatuses)
              .build();

  private List<String> ids = List.of();
  private List<Integer> statuses = List.of();

  public StateBuilderRequestBodyType() {}

  public StateBuilderRequestBodyType(final List<String> ids) {
    this.ids = ids;
  }

  public StateBuilderRequestBodyType(final List<String> ids, final List<Integer> statuses) {
    this.ids = ids;
    this.statuses = statuses;
  }

  public List<String> getIds() {
    return ids;
  }

  public List<Integer> getStatuses() {
    return statuses;
  }

  public Optional<List<String>> getMaybeIds() {
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
  }

  public Optional<List<Integer>> getMaybeStatuses() {
    return statuses.isEmpty() ? Optional.empty() : Optional.of(statuses);
  }

  public void setIds(final Optional<List<String>> ids) {
    ids.ifPresent(i -> this.ids = i);
  }

  public void setStatuses(final Optional<List<Integer>> statuses) {
    statuses.ifPresent(s -> this.statuses = s);
  }
}
