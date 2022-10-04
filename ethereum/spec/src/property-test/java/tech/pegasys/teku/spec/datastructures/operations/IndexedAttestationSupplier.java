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

package tech.pegasys.teku.spec.datastructures.operations;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;
import net.jqwik.api.Combinators;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class IndexedAttestationSupplier implements ArbitrarySupplier<IndexedAttestation> {
  @Override
  public Arbitrary<IndexedAttestation> get() {
    Arbitrary<Integer> seed = Arbitraries.integers();
    Arbitrary<SpecMilestone> milestone = Arbitraries.of(SpecMilestone.class);
    Arbitrary<Eth2Network> network = Arbitraries.of(Eth2Network.class);
    Arbitrary<Spec> spec = Combinators.combine(milestone, network).as(TestSpecFactory::create);
    Arbitrary<DataStructureUtil> dsu = Combinators.combine(seed, spec).as(DataStructureUtil::new);
    return dsu.map(DataStructureUtil::randomIndexedAttestation);
  }
}
