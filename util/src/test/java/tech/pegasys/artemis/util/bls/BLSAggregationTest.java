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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class BLSAggregationTest {

  @Test
  void succeedsWhenAggregatingASingleSignatureReturnsTheSameSignature() throws BLSException {
    BLSSignature signature = BLSSignature.random();
    assertEquals(signature, BLSSignature.aggregate(Arrays.asList(signature)));
  }

  @Test
  void succeedsWhenAggregatingASinglePublicKeyReturnsTheSamePublicKey() {
    Bytes48 publicKeyCompressed = Bytes48.wrap(BLSKeyPair.random().publicKey().toBytes());
    assertEquals(publicKeyCompressed, BLSPublicKey.aggregate(Arrays.asList(publicKeyCompressed)));
  }

  @Test
  void succeedsWhenPassingEmptySignatureToAggregateSignaturesThrowsBLSException() {
    BLSSignature signature1 = BLSSignature.random();
    BLSSignature signature2 = BLSSignature.empty();
    BLSSignature signature3 = BLSSignature.random();
    assertThrows(
        BLSException.class,
        () -> BLSSignature.aggregate(Arrays.asList(signature1, signature2, signature3)));
  }

  @Test
  void succeedsWhenSendingDifferentNumbersOfKeysAndMessagesThrowsIllegalArgumentException() {
    BLSSignature signature = BLSSignature.random();

    // Two keys
    Bytes48 publicKey = Bytes48.wrap(PublicKey.random().toBytes());
    List<Bytes48> publicKeys = Arrays.asList(publicKey, publicKey);

    // Three messages
    Bytes message = Bytes.wrap("Ceci n'est pas une pipe".getBytes(UTF_8));
    List<Bytes> messages = Arrays.asList(message, message, message);

    assertThrows(
        IllegalArgumentException.class, () -> signature.checkSignature(publicKeys, messages, 0));
  }

  @Test
  void succeedsWhenCorrectlySigningAndVerifyingAggregateSignaturesReturnsTrue()
      throws BLSException {
    Bytes message1 = Bytes.wrap("Message One".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Message Two".getBytes(UTF_8));

    BLSKeyPair keyPair1 = BLSKeyPair.random();
    BLSKeyPair keyPair2 = BLSKeyPair.random();
    BLSKeyPair keyPair3 = BLSKeyPair.random();
    BLSKeyPair keyPair4 = BLSKeyPair.random();

    // 1 & 2 sign message1; 3 & 4 sign message2
    BLSSignature signature1 = BLSSignature.sign(keyPair1, message1, 0);
    BLSSignature signature2 = BLSSignature.sign(keyPair2, message1, 0);
    BLSSignature signature3 = BLSSignature.sign(keyPair3, message2, 0);
    BLSSignature signature4 = BLSSignature.sign(keyPair4, message2, 0);

    // Aggregate keys 1 & 2, and keys 3 & 4
    Bytes48 aggregatePublicKey12 =
        BLSPublicKey.aggregate(
            Arrays.asList(
                Bytes48.wrap(keyPair1.getBlsPublicKey().getPublicKey().toBytes()),
                Bytes48.wrap(keyPair2.getBlsPublicKey().getPublicKey().toBytes())));
    Bytes48 aggregatePublicKey34 =
        BLSPublicKey.aggregate(
            Arrays.asList(
                Bytes48.wrap(keyPair3.getBlsPublicKey().getPublicKey().toBytes()),
                Bytes48.wrap(keyPair4.getBlsPublicKey().getPublicKey().toBytes())));

    // Aggregate the signatures
    BLSSignature aggregateSignature =
        BLSSignature.aggregate(Arrays.asList(signature1, signature2, signature3, signature4));

    // Verify the aggregate signatures and keys
    assertTrue(
        aggregateSignature.checkSignature(
            Arrays.asList(aggregatePublicKey12, aggregatePublicKey34),
            Arrays.asList(message1, message2),
            0));
  }

  @Test
  void succeedsWhenIncorrectlySigningAndVerifyingAggregateSignaturesReturnsFalse()
      throws BLSException {
    Bytes message1 = Bytes.wrap("Message One".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Message Two".getBytes(UTF_8));

    BLSKeyPair keyPair1 = BLSKeyPair.random();
    BLSKeyPair keyPair2 = BLSKeyPair.random();
    BLSKeyPair keyPair3 = BLSKeyPair.random();
    BLSKeyPair keyPair4 = BLSKeyPair.random();

    // 1 & 2 sign message1; 3 & 4 sign message2
    BLSSignature signature1 = BLSSignature.sign(keyPair1, message1, 0);
    BLSSignature signature2 = BLSSignature.sign(keyPair2, message1, 0);
    BLSSignature signature3 = BLSSignature.sign(keyPair3, message2, 0);
    BLSSignature signature4 = BLSSignature.sign(keyPair4, message2, 0);

    // Aggregate keys 1 & 2, and keys 3 & 4
    Bytes48 aggregatePublicKey12 =
        BLSPublicKey.aggregate(
            Arrays.asList(
                Bytes48.wrap(keyPair1.getBlsPublicKey().getPublicKey().toBytes()),
                Bytes48.wrap(keyPair2.getBlsPublicKey().getPublicKey().toBytes())));
    Bytes48 aggregatePublicKey34 =
        BLSPublicKey.aggregate(
            Arrays.asList(
                Bytes48.wrap(keyPair3.getBlsPublicKey().getPublicKey().toBytes()),
                Bytes48.wrap(keyPair4.getBlsPublicKey().getPublicKey().toBytes())));

    // Aggregate the signatures
    BLSSignature aggregateSignature =
        BLSSignature.aggregate(Arrays.asList(signature1, signature2, signature3, signature4));

    // Verify the aggregate signatures and keys: note, we have swapped the messages
    assertFalse(
        aggregateSignature.checkSignature(
            Arrays.asList(aggregatePublicKey12, aggregatePublicKey34),
            Arrays.asList(message2, message1),
            0));
  }
}
