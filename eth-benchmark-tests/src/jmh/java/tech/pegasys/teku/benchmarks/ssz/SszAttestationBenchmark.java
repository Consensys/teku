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
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SszAttestationBenchmark extends SszAbstractContainerBenchmark<Attestation> {

  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1);
  private static final Attestation anAttestation = dataStructureUtil.randomAttestation();

  private static final SszBitlist aggregation_bits = anAttestation.getAggregationBits();
  private static final AttestationData attestationData = anAttestation.getData();
  private static final BLSSignature signature = anAttestation.getAggregateSignature();

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
