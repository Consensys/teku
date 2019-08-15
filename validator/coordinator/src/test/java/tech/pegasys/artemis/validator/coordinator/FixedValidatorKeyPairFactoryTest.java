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

package tech.pegasys.artemis.validator.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

class FixedValidatorKeyPairFactoryTest {
  private final FixedValidatorKeyPairFactory factory = new FixedValidatorKeyPairFactory();

  @Test
  public void shouldCreateDeterministic() {
    final List<BLSKeyPair> keyPairs = factory.generateKeyPairs(3, 6);
    assertEquals(keyPairs.size(), 4); // Start and end inclusive
    assertEquals(
        Bytes.fromHexString(
            "0x00000000000000000000000000000000203DCC86E89E3622F23773C9428FBF4730A13089B0EAD98AB751B5CF814ABB1A"),
        keyPairs.get(0).getSecretKey().getSecretKey().toBytes());
    assertEquals(
        Bytes.fromHexString(
            "0x00000000000000000000000000000000119F962794F815E4F21AE048E66286A930F69B51BBC711367A6A4B3A57E3F5FF"),
        keyPairs.get(1).getSecretKey().getSecretKey().toBytes());
    assertEquals(
        Bytes.fromHexString(
            "0x0000000000000000000000000000000035C5231125A7CD2B9F2B6C1CD7B885B7C283D3D7C4313A82BD540AA4639BB91B"),
        keyPairs.get(2).getSecretKey().getSecretKey().toBytes());
    assertEquals(
        Bytes.fromHexString(
            "0x000000000000000000000000000000001B634EDDE3656467018FDD8FF2BBD8E7BD991A23E3E231BEB34498EB91B0855A"),
        keyPairs.get(3).getSecretKey().getSecretKey().toBytes());
  }
}
