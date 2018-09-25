package net.consensys.beaconchain.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BouncyCastleMessageDigestFactory {

  private static final BouncyCastleProvider securityProvider = new BouncyCastleProvider();

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest create(String algorithm) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(algorithm, securityProvider);
  }

}
