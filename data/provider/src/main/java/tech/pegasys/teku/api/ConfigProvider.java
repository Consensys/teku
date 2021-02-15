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

package tech.pegasys.teku.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.response.v1.config.GetForkScheduleResponse;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;

public class ConfigProvider {
  final SpecProvider specProvider;

  public ConfigProvider(final SpecProvider specProvider) {
    this.specProvider = specProvider;
  }

  public GetSpecResponse getConfig() {
    final Map<String, String> configAttributes = new HashMap<>();
    specProvider
        // Display genesis spec, for now
        .atEpoch(UInt64.ZERO)
        .getConstants()
        .getRawConstants()
        .forEach(
            (k, v) -> {
              configAttributes.put(k, "" + v);
            });
    return new GetSpecResponse(configAttributes);
  }

  public GetForkScheduleResponse getForkSchedule() {
    final List<Fork> forkList =
        specProvider.getForkManifest().getForkSchedule().stream()
            .map(Fork::new)
            .collect(Collectors.toList());
    return new GetForkScheduleResponse(forkList);
  }

  public SpecConstants getGenesisSpecConstants() {
    return specProvider.getGenesisSpecConstants();
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return specProvider.atSlot(slot).getBeaconStateUtil().computeEpochAtSlot(slot);
  }
}
