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

package tech.pegasys.teku.benchmarks.ssz;

import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.util.CustomRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.schema.SszSchema;

public class SszBeaconBlockBodyBenchmark extends SszAbstractContainerBenchmark<BeaconBlockBody> {

  private static final Spec spec = SpecFactory.createMinimal();
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1, spec);
  private static final BeaconBlockBody beaconBlockBody = dataStructureUtil.randomBeaconBlockBody();

  @Override
  protected BeaconBlockBody createContainer() {
    return spec.getGenesisSpec()
        .getSchemaDefinitions()
        .getBeaconBlockBodySchema()
        .createBlockBody(
            builder ->
                builder
                    .randaoReveal(beaconBlockBody.getRandao_reveal())
                    .eth1Data(beaconBlockBody.getEth1_data())
                    .graffiti(beaconBlockBody.getGraffiti())
                    .attestations(beaconBlockBody.getAttestations())
                    .proposerSlashings(beaconBlockBody.getProposer_slashings())
                    .attesterSlashings(beaconBlockBody.getAttester_slashings())
                    .deposits(beaconBlockBody.getDeposits())
                    .voluntaryExits(beaconBlockBody.getVoluntary_exits()));
  }

  @Override
  protected SszSchema<BeaconBlockBody> getContainerType() {
    return SszSchema.as(
        BeaconBlockBody.class,
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema());
  }

  @Override
  public void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    SszBenchUtil.iterateData(bbb, bh);
  }

  public static void main(String[] args) {
    SszBeaconBlockBodyBenchmark benchmark = new SszBeaconBlockBodyBenchmark();
    BeaconBlockBody blockBody = benchmark.createContainer();
    new CustomRunner(1000000, 2000).withBench(bh -> benchmark.iterateData(blockBody, bh)).run();
  }
}
