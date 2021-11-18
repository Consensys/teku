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

package tech.pegasys.teku.services.executionengine;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.spec.Spec;

public class ExecutionEngineConfiguration {

  private final Spec spec;
  private final Optional<String> endpoint;

  private ExecutionEngineConfiguration(final Spec spec, final Optional<String> endpoint) {
    this.spec = spec;
    this.endpoint = endpoint;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return endpoint.isPresent();
  }

  public Spec getSpec() {
    return spec;
  }

  public String getEndpoint() {
    return endpoint.orElseThrow();
  }

  public static class Builder {
    private Spec spec;
    private Optional<String> endpoint = Optional.empty();

    private Builder() {}

    public ExecutionEngineConfiguration build() {
      validate();
      return new ExecutionEngineConfiguration(spec, endpoint);
    }

    private void validate() {
      checkNotNull(spec, "Must specify a spec");
    }

    public Builder endpoint(final String endpoint) {
      this.endpoint = Optional.ofNullable(endpoint);
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }
  }
}
