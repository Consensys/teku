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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;

public class BuilderApiResponse<T> {

  private final SpecMilestone version;
  private final T data;

  public BuilderApiResponse(final SpecMilestone version, final T data) {
    this.version = version;
    this.data = data;
  }

  public SpecMilestone getVersion() {
    return version;
  }

  public T getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BuilderApiResponse<?> that = (BuilderApiResponse<?>) o;
    return Objects.equals(version, that.version) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("version", version).add("data", data).toString();
  }

  public static class Builder<T> {
    private SpecMilestone version;
    private T data;

    private Builder() {}

    public static <T> Builder<T> builder() {
      return new Builder<>();
    }

    public Builder<T> version(final SpecMilestone version) {
      this.version = version;
      return this;
    }

    public Builder<T> data(final T data) {
      this.data = data;
      return this;
    }

    public BuilderApiResponse<T> build() {
      return new BuilderApiResponse<>(version, data);
    }
  }

  public static <T> DeserializableTypeDefinition<BuilderApiResponse<T>> createTypeDefinition(
      final DeserializableTypeDefinition<T> dataTypeDefinition) {
    final DeserializableObjectTypeDefinitionBuilder<BuilderApiResponse<T>, Builder<T>>
        typeDefinitionBuilder = DeserializableTypeDefinition.object();
    return typeDefinitionBuilder
        .initializer(BuilderApiResponse.Builder::builder)
        .withField(
            "version",
            EthereumTypes.MILESTONE_TYPE,
            BuilderApiResponse::getVersion,
            BuilderApiResponse.Builder::version)
        .withField(
            "data",
            dataTypeDefinition,
            BuilderApiResponse::getData,
            BuilderApiResponse.Builder::data)
        .finisher(BuilderApiResponse.Builder::build)
        .build();
  }
}
