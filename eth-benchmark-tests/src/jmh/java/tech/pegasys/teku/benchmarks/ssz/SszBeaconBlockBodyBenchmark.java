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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

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
            beaconBlockBody.getRandao_reveal(),
            beaconBlockBody.getEth1_data(),
            beaconBlockBody.getGraffiti(),
            beaconBlockBody.getProposer_slashings(),
            beaconBlockBody.getAttester_slashings(),
            beaconBlockBody.getAttestations(),
            beaconBlockBody.getDeposits(),
            beaconBlockBody.getVoluntary_exits());
  }

  @Override
  protected SszSchema<BeaconBlockBody> getContainerType() {
    return BeaconBlockBody.getSszSchema();
  }

  @Override
  protected void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    SszBenchUtil.iterateData(bbb, bh);
  }

  public static void main(String[] args) {
    new SszBeaconBlockBodyBenchmark().customRun(10, 10000);
  }

  public static void main1(String[] args) {
    SszBeaconBlockBodyBenchmark bench = new SszBeaconBlockBodyBenchmark();
    long cnt = 0;
    while (true) {
      bench.benchDeserializeAndIterate(bench.blackhole);
      //      bench.benchIterate(bench.blackhole);
      if (cnt % 10000 == 0) {
        System.out.println("Iterated: " + cnt);
      }
      cnt++;
    }
  }
}
