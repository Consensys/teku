/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBeaconBlockSupplier extends DataStructureUtilSupplier<SignedBeaconBlock> {

  /**
   * Few fields (e.g. blob_kzg_commitments ) are removed in Gloas so for property testing, we only
   * consider blocks until Fulu. For generic testing not related to >= Deneb specific features, use
   * {@link tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBeaconBlockSupplier}
   */
  private static final SpecMilestone MAXIMUM_SPEC_MILESTONE = SpecMilestone.FULU;

  public SignedBeaconBlockSupplier() {
    super(DataStructureUtil::randomSignedBeaconBlock, SpecMilestone.DENEB, MAXIMUM_SPEC_MILESTONE);
  }
}
