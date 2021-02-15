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

import java.util.Optional;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class SszAttestationBenchmark extends SszAbstractContainerBenchmark<Attestation> {
  private static SpecProvider specProvider = StubSpecProvider.createMainnet();
  private static final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(1, Optional.of(specProvider.getGenesisSpec()));
  private static final Attestation anAttestation = dataStructureUtil.randomAttestation();

  private static final Bitlist aggregation_bits = anAttestation.getAggregation_bits();
  private static final AttestationData attestationData = anAttestation.getData();
  private static final BLSSignature signature = anAttestation.getAggregate_signature();

  @Override
  protected Attestation createContainer() {
    return new Attestation(aggregation_bits, attestationData, signature);
  }

  @Override
  protected SszSchema<Attestation> getContainerType() {
    return Attestation.SSZ_SCHEMA;
  }

  @Override
  protected void iterateData(Attestation pa, Blackhole bh) {
    SszBenchUtil.iterateData(pa, bh);
  }
}
