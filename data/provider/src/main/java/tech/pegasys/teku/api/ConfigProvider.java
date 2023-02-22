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

package tech.pegasys.teku.api;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.response.v1.config.GetForkScheduleResponse;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;

public class ConfigProvider {
  final Spec spec;

  public ConfigProvider(final Spec spec) {
    this.spec = spec;
  }

  public GetSpecResponse getConfig() {
    final tech.pegasys.teku.api.GetSpecResponse configuration =
        new tech.pegasys.teku.api.GetSpecResponse(spec.getGenesisSpecConfig());

    return new GetSpecResponse(configuration.getConfigMap());
  }

  public SpecConfig getGenesisSpec() {
    return spec.atEpoch(UInt64.ZERO).getConfig();
  }

  public static String formatValue(final Object v) {
    if (v instanceof UInt256) {
      return ((UInt256) v).toDecimalString();
    }
    return v.toString();
  }

  public GetForkScheduleResponse getForkSchedule() {
    final List<Fork> forkList =
        spec.getForkSchedule().getForks().stream().map(Fork::new).collect(Collectors.toList());
    return new GetForkScheduleResponse(forkList);
  }

  public List<tech.pegasys.teku.spec.datastructures.state.Fork> getStateForkSchedule() {
    return spec.getForkSchedule().getFullForkList();
  }

  public SpecConfig getGenesisSpecConfig() {
    return spec.getGenesisSpecConfig();
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return spec.computeEpochAtSlot(slot);
  }
}
