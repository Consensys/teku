package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KeyStoreJsonTest {
    private static final String sCryptJson = "{\n" +
            "    \"crypto\": {\n" +
            "        \"kdf\": {\n" +
            "            \"function\": \"scrypt\",\n" +
            "            \"params\": {\n" +
            "                \"dklen\": 32,\n" +
            "                \"n\": 262144,\n" +
            "                \"p\": 1,\n" +
            "                \"r\": 8,\n" +
            "                \"salt\": \"d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"\n" +
            "            },\n" +
            "            \"message\": \"\"\n" +
            "        },\n" +
            "        \"checksum\": {\n" +
            "            \"function\": \"sha256\",\n" +
            "            \"params\": {},\n" +
            "            \"message\": \"149aafa27b041f3523c53d7acba1905fa6b1c90f9fef137568101f44b531a3cb\"\n" +
            "        },\n" +
            "        \"cipher\": {\n" +
            "            \"function\": \"aes-128-ctr\",\n" +
            "            \"params\": {\n" +
            "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n" +
            "            },\n" +
            "            \"message\": \"54ecc8863c0550351eee5720f3be6a5d4a016025aa91cd6436cfec938d6a8d30\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n" +
            "    \"path\": \"m/12381/60/3141592653/589793238\",\n" +
            "    \"uuid\": \"1d85ae20-35c5-4611-98e8-aa14a633906f\",\n" +
            "    \"version\": 4\n" +
            "}";
    private static final String pbkdf2Json = "{\n" +
            "    \"crypto\": {\n" +
            "        \"kdf\": {\n" +
            "            \"function\": \"pbkdf2\",\n" +
            "            \"params\": {\n" +
            "                \"dklen\": 32,\n" +
            "                \"c\": 262144,\n" +
            "                \"prf\": \"hmac-sha256\",\n" +
            "                \"salt\": \"d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3\"\n" +
            "            },\n" +
            "            \"message\": \"\"\n" +
            "        },\n" +
            "        \"checksum\": {\n" +
            "            \"function\": \"sha256\",\n" +
            "            \"params\": {},\n" +
            "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n" +
            "        },\n" +
            "        \"cipher\": {\n" +
            "            \"function\": \"aes-128-ctr\",\n" +
            "            \"params\": {\n" +
            "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n" +
            "            },\n" +
            "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n" +
            "    \"path\": \"m/12381/60/0/0\",\n" +
            "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n" +
            "    \"version\": 4\n" +
            "}";

    private static final String missingKdfParamJson = "{\n" +
            "    \"crypto\": {\n" +
            "        \"kdf\": {\n" +
            "            \"function\": \"pbkdf2\",\n" +
            "            \"message\": \"\"\n" +
            "        },\n" +
            "        \"checksum\": {\n" +
            "            \"function\": \"sha256\",\n" +
            "            \"params\": {},\n" +
            "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n" +
            "        },\n" +
            "        \"cipher\": {\n" +
            "            \"function\": \"aes-128-ctr\",\n" +
            "            \"params\": {\n" +
            "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n" +
            "            },\n" +
            "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n" +
            "    \"path\": \"m/12381/60/0/0\",\n" +
            "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n" +
            "    \"version\": 4\n" +
            "}";

    private static final String emptyKdfParams = "{\n" +
            "    \"crypto\": {\n" +
            "        \"kdf\": {\n" +
            "            \"function\": \"pbkdf2\",\n" +
            "            \"params\": {},\n" +
            "            \"message\": \"\"\n" +
            "        },\n" +
            "        \"checksum\": {\n" +
            "            \"function\": \"sha256\",\n" +
            "            \"params\": {},\n" +
            "            \"message\": \"18b148af8e52920318084560fd766f9d09587b4915258dec0676cba5b0da09d8\"\n" +
            "        },\n" +
            "        \"cipher\": {\n" +
            "            \"function\": \"aes-128-ctr\",\n" +
            "            \"params\": {\n" +
            "                \"iv\": \"264daa3f303d7259501c93d997d84fe6\"\n" +
            "            },\n" +
            "            \"message\": \"a9249e0ca7315836356e4c7440361ff22b9fe71e2e2ed34fc1eb03976924ed48\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"pubkey\": \"9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07\",\n" +
            "    \"path\": \"m/12381/60/0/0\",\n" +
            "    \"uuid\": \"64625def-3331-4eea-ab6f-782f3ed16a83\",\n" +
            "    \"version\": 4\n" +
            "}";
    @Test
    void parseSCryptTestVector() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        final KeyStore keyStore = objectMapper.readValue(sCryptJson, KeyStore.class);
        Assertions.assertNotNull(keyStore);
        Assertions.assertNotNull(keyStore.getCrypto().getKdf().getParams());
        Assertions.assertTrue(keyStore.getCrypto().getKdf().getParams() instanceof SCryptParams);
    }

    @Test
    void parsePbKdf2TestVector() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        final KeyStore keyStore = objectMapper.readValue(pbkdf2Json, KeyStore.class);
        Assertions.assertNotNull(keyStore);
        final Pbkdf2Params params = (Pbkdf2Params) keyStore.getCrypto().getKdf().getParams();
        Assertions.assertNotNull(params);
        Assertions.assertEquals("hmac-sha256", params.getPrf());
    }

    @Test
    void parseMissingKdfParamsthrowsException() {
        ObjectMapper objectMapper = new ObjectMapper();
        Assertions.assertThrows(JsonMappingException.class, () -> objectMapper.readValue(missingKdfParamJson, KeyStore.class));
    }

    @Test
    void parseWithEmptyParamThrowsException() throws Exception{
        ObjectMapper objectMapper = new ObjectMapper();
        final KeyStore keyStore = objectMapper.readValue(emptyKdfParams, KeyStore.class);
        Assertions.assertNotNull(keyStore);
        final Pbkdf2Params params = (Pbkdf2Params) keyStore.getCrypto().getKdf().getParams();
        Assertions.assertNotNull(params);
        Assertions.assertEquals("hmac-sha256", params.getPrf());
    }

}