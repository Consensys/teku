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

package tech.pegasys.teku.spec.datastructures.builder.versions.deneb;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix.BuilderBidBuilderBellatrix;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderBidBuilderDeneb extends BuilderBidBuilderBellatrix {

  private BuilderBidSchemaDeneb schema;
  protected BlindedBlobsBundle blindedBlobsBundle;

  public BuilderBidBuilderDeneb schema(final BuilderBidSchemaDeneb schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public BuilderBidBuilder blindedBlobsBundle(final BlindedBlobsBundle blindedBlobsBundle) {
    this.blindedBlobsBundle = blindedBlobsBundle;
    return this;
  }

  @Override
  public BuilderBid build() {
    return new BuilderBidDenebImpl(
        schema, header, blindedBlobsBundle, SszUInt256.of(value), new SszPublicKey(publicKey));
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blindedBlobsBundle, "blindedBlobsBundle must be specified");
  }
}
