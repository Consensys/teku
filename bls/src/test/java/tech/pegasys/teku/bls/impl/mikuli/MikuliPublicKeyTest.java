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

package tech.pegasys.teku.bls.impl.mikuli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.milagro.amcl.BLS381.BIG;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.PublicKeyTest;

public class MikuliPublicKeyTest extends PublicKeyTest {

  @Override
  protected BLS12381 getBls() {
    return MikuliBLS12381.INSTANCE;
  }

  @Test
  void zeroSecretKeyGivesPointAtInfinity() {
    MikuliPublicKey pubKey = new MikuliPublicKey(new MikuliSecretKey(new Scalar(new BIG(0))));
    assertTrue(pubKey.g1Point().ecpPoint().is_infinity());
  }
}
