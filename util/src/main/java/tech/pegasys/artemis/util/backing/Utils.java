package tech.pegasys.artemis.util.backing;

public class Utils {

  public static int nextPowerOf2(int x) {
    return x <= 1 ? 1 : Integer.highestOneBit(x - 1) << 1;
  }
}
