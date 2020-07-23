package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BlstPublicKeyTest {

  @BeforeAll
  static void setup() {
    BlstBLS12381.INSTANCE.hashCode();
  }

  @Test
  void infinityPublicKey() {
    BlstPublicKey inf1 = BlstPublicKey.fromBytes(
        Bytes48.fromHexString(
            "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    Bytes bytes = inf1.toBytesUncompressed();

    Bytes48 x =
        Bytes48.fromHexString(
            "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    Bytes48 y =
        Bytes48.fromHexString(
            "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001");
    BlstPublicKey publicKey = BlstPublicKey.fromBytesUncompressed(bytes);
    publicKey.forceValidation();
  }


  @Test
  void succeedsWhenInvalidPublicKeyIsInvalid() {
    Bytes48 invalidPublicKeyBytes = Bytes48.fromHexString(
        "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd");
    assertThatThrownBy(
            () -> {
              BlstPublicKey publicKey = BlstPublicKey.fromBytes(invalidPublicKeyBytes);
              Bytes uncompressed = publicKey.toBytesUncompressed();
              publicKey.forceValidation();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }
}
