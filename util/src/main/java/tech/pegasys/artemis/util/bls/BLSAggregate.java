/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.bls;

import java.util.List;

/** Stub to allow every to compile. TODO - remove */
public class BLSAggregate {

  public static BLSPublicKey bls_aggregate_pubkeys(List<BLSPublicKey> pubKeys) {
    return BLSPublicKey.random(0);
  }

  public static BLSSignature bls_aggregate_signatures(List<BLSSignature> signatures) {
    return BLSSignature.random(0);
  }
}
