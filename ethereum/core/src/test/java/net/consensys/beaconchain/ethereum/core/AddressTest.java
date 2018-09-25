package net.consensys.beaconchain.ethereum.vm;

import net.consensys.beaconchain.ethereum.core.Address;
import net.consensys.beaconchain.util.bytes.BytesValue;

import org.junit.Assert;
import org.junit.Test;

public class AddressTest {

  @Test
  public void accountAddressToString() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Assert.assertEquals("0x0000000000000000000000000000000000101010", addr.toString());
  }

  @Test
  public void accountAddressEquals() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr, addr2);
  }

  @Test
  public void accountAddresHashCode() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr.hashCode(), addr2.hashCode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidAccountAddress() {
    Address.wrap(BytesValue.fromHexString("0x00101010"));
  }
}
