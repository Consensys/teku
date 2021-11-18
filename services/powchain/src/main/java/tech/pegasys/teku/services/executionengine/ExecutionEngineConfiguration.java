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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.spec.Spec;

public class ExecutionEngineConfiguration {

  private final Spec spec;
  private final List<String> endpoints;

  private ExecutionEngineConfiguration(final Spec spec, final List<String> endpoints) {
    this.spec = spec;
    this.endpoints = endpoints;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return !endpoints.isEmpty();
  }

  public Spec getSpec() {
    return spec;
  }

  public List<String> getEndpoints() {
    return endpoints;
  }

  public static class Builder {
    private Spec spec;
    private List<String> endpoints = new ArrayList<>();

    private Builder() {}

    public ExecutionEngineConfiguration build() {
      validate();
      return new ExecutionEngineConfiguration(spec, endpoints);
    }

    private void validate() {
      checkNotNull(spec, "Must specify a spec");
    }

    public Builder endpoints(final List<String> endpoints) {
      checkNotNull(endpoints);
      this.endpoints = endpoints.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }
  }
}
