package net.consensys.beaconchain.state;

import static net.consensys.beaconchain.state.CrystallizedState.CrystallizedStateOperators.fromBytes3;
import static net.consensys.beaconchain.state.CrystallizedState.CrystallizedStateOperators.toBytes3;
import static net.consensys.beaconchain.state.CrystallizedState.CrystallizedStateOperators.shuffle;
import static net.consensys.beaconchain.state.CrystallizedState.CrystallizedStateOperators.split;

import net.consensys.beaconchain.datastructures.ValidatorRecord;

import net.consensys.beaconchain.ethereum.core.Hash;


import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.web3j.abi.datatypes.generated.Bytes3;


public class CrystallizedStateTest {


  /** Validates int to Bytes3 conversion. */
  @Test
  public void toBytes3Test() {

    int seed = 212420;
    byte[] bytes = {(byte) 3, (byte) 829, (byte) 212420};

    Bytes3 expected = new Bytes3(bytes);
    Bytes3 actual = toBytes3(seed);

    assertEquals(expected, actual);
  }


  /** Validate Bytes3 to int conversion. */
  @Test
  public void fromBytes3Test() {

    byte[] src = {(byte) 3, (byte) 829, (byte) 212420};
//    Hash src = ;
    int pos = 0;

    int expected = 212420;
    int actual = fromBytes3(src, pos);

    assertEquals(expected, actual);
  }


//  /** Validate shuffle. */
//  @Test
//  public void shuffleTest() {
//
//    ValidatorRecord[] expected = new ValidatorRecord[];
//    ValidatorRecord[] actual = shuffle();
//
//    assertEquals(expected, actual);
//  }
//
//
//  /** Validate split. */
//  @Test
//  public void splitTest() {
//
//    ValidatorRecord[][] expected = new ValidatorRecord[][];
//    ValidatorRecord[][] actual = split();
//
//    assertEquals(expected, actual);
//  }




}
