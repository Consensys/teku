/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidBellatrix
    extends Container3<BuilderBidBellatrix, ExecutionPayloadHeader, SszUInt256, SszPublicKey>
    implements BuilderBid {

  BuilderBidBellatrix(final BuilderBidSchemaBellatrix schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public BuilderBidBellatrix(
      final BuilderBidSchemaBellatrix schema,
      final ExecutionPayloadHeader header,
      final SszUInt256 value,
      final SszPublicKey publicKey) {
    super(schema, header, value, publicKey);
  }

  @Override
  public ExecutionPayloadHeader getHeader() {
    return getField0();
  }

  @Override
  public Optional<BlindedBlobsBundle> getOptionalBlindedBlobsBundle() {
    return Optional.empty();
  }

  @Override
  public UInt256 getValue() {
    return getField1().get();
  }

  @Override
  public BLSPublicKey getPublicKey() {
    return getField2().getBLSPublicKey();
  }

  @Override
  public BuilderBidSchemaBellatrix getSchema() {
    return (BuilderBidSchemaBellatrix) super.getSchema();
  }
}
