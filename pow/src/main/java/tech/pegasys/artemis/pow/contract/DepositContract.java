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

package tech.pegasys.artemis.pow.contract;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.StaticArray;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.0.1.
 */
public class DepositContract extends Contract {
  private static final String BINARY =
      "0x600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009e57600080fd5b6101406000601f818352015b600061014051602081106100bd57600080fd5b600060c052602060c020015460208261016001015260208101905061014051602081106100e957600080fd5b600060c052602060c020015460208261016001015260208101905080610160526101609050805160208201209050606051600161014051018060405190131561013157600080fd5b809190121561013f57600080fd5b6020811061014c57600080fd5b600060c052602060c0200155606051600161014051018060405190131561017257600080fd5b809190121561018057600080fd5b6020811061018d57600080fd5b600060c052602060c020015460605160016101405101806040519013156101b357600080fd5b80919012156101c157600080fd5b602081106101ce57600080fd5b600160c052602060c02001555b81516001018083528114156100aa575b505061122c56600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a0526000156101a3575b6101605261014052601860086020820661020001602082840111156100bf57600080fd5b60208061022082610140600060046015f15050818152809050905090508051602001806102c0828460006004600a8704601201f16100fc57600080fd5b50506102c05160206001820306601f82010390506103206102c0516008818352015b8261032051111561012e5761014a565b6000610320516102e001535b815160010180835281141561011e575b50505060206102a05260406102c0510160206001820306601f8201039050610280525b60006102805111151561017f5761019b565b602061028051036102a00151602061028051036102805261016d565b610160515650005b638067328960005114156104f957602060046101403734156101c457600080fd5b67ffffffffffffffff6101405111156101dc57600080fd5b6101405161016051610180516101a05163b0429c706101c052610140516101e0526101e0516006580161009b565b506102405260006102a0525b6102405160206001820306601f82010390506102a05110151561023857610251565b6102a05161026001526102a0516020016102a052610216565b6101a052610180526101605261014052610240805160200180610160828460006004600a8704601201f161028457600080fd5b50506101608060200151600082518060209013156102a157600080fd5b80919012156102af57600080fd5b806020036101000a82049050905090506102c05260006102e05261030060006008818352015b6102e051600860008112156102f2578060000360020a82046102f9565b8060020a82025b905090506102e05260ff6102c05116610320526102e051610320516102e05101101561032457600080fd5b610320516102e051016102e0526102c0517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8600081121561036d578060000360020a8204610374565b8060020a82025b905090506102c0525b81516001018083528114156102d5575b50506101405161016051610180516101a0516101c0516101e05161020051610220516102405161026051610280516102a0516102c0516102e05163b0429c70610340526102e05161036052610360516006580161009b565b506103c0526000610420525b6103c05160206001820306601f8201039050610420511015156104135761042c565b610420516103e0015261042051602001610420526103f1565b6102e0526102c0526102a05261028052610260526102405261022052610200526101e0526101c0526101a0526101805261016052610140526103c0805160200180610480828460006004600a8704601201f161048757600080fd5b50506104805160206001820306601f82010390506104e0610480516008818352015b826104e05111156104b9576104d5565b60006104e0516104a001535b81516001018083528114156104a9575b5050506020610460526040610480510160206001820306601f8201039050610460f3005b63c5f2892f600051141561063257341561051257600080fd5b6000610140526002546101605261018060006020818352015b6001600261053857600080fd5b6002610160510614156105a2576000610180516020811061055857600080fd5b600160c052602060c02001546020826102200101526020810190506101405160208261022001015260208101905080610220526102209050805160208201209050610140526105fb565b6000610140516020826101a001015260208101905061018051602081106105c857600080fd5b600060c052602060c02001546020826101a0010152602081019050806101a0526101a09050805160208201209050610140525b610160600261060957600080fd5b60028151048152505b815160010180835281141561052b575b50506101405160005260206000f3005b6398b1e06a600051141561100e5760206004610140376102206004356004016101603761020060043560040135111561066a57600080fd5b633b9aca006103c0526103c05161068057600080fd5b6103c05134046103a052633b9aca006103a051101561069e57600080fd5b6407735940006103a05111156106b357600080fd5b6002546103e05242610400526000606061070060246380673289610680526103a0516106a05261069c6000305af16106ea57600080fd5b61072060088060208461084001018260208501600060046012f150508051820191505060606107e06024638067328961076052610400516107805261077c6000305af161073657600080fd5b61080060088060208461084001018260208501600060046012f15050805182019150506101606102008060208461084001018260208501600060046045f150508051820191505080610840526108409050805160200180610420828460006004600a8704601201f16107a757600080fd5b50506000610aa0526002610ac052610ae060006020818352015b6000610ac0516107d057600080fd5b610ac0516103e05160016103e0510110156107ea57600080fd5b60016103e05101061415156107fe5761086a565b610aa060605160018251018060405190131561081957600080fd5b809190121561082757600080fd5b815250610ac08051151561083c576000610856565b600281516002835102041461085057600080fd5b60028151025b8152505b81516001018083528114156107c1575b5050610420805160208201209050610b0052610b2060006020818352015b610aa051610b205112156108ef576000610b2051602081106108a957600080fd5b600160c052602060c0200154602082610b40010152602081019050610b0051602082610b4001015260208101905080610b4052610b409050805160208201209050610b00525b5b8151600101808352811415610888575b5050610b0051610aa0516020811061091757600080fd5b600160c052602060c0200155600280546001825401101561093757600080fd5b60018154018155506020610c40600463c5f2892f610be052610bfc6000305af161096057600080fd5b610c4051610bc0526060610ce060246380673289610c60526103e051610c8052610c7c6000305af161099157600080fd5b610d00805160200180610d40828460006004600a8704601201f16109b457600080fd5b5050610bc051610e0052600160c052602060c02054610e60526001600160c052602060c0200154610e80526002600160c052602060c0200154610ea0526003600160c052602060c0200154610ec0526004600160c052602060c0200154610ee0526005600160c052602060c0200154610f00526006600160c052602060c0200154610f20526007600160c052602060c0200154610f40526008600160c052602060c0200154610f60526009600160c052602060c0200154610f8052600a600160c052602060c0200154610fa052600b600160c052602060c0200154610fc052600c600160c052602060c0200154610fe052600d600160c052602060c020015461100052600e600160c052602060c020015461102052600f600160c052602060c0200154611040526010600160c052602060c0200154611060526011600160c052602060c0200154611080526012600160c052602060c02001546110a0526013600160c052602060c02001546110c0526014600160c052602060c02001546110e0526015600160c052602060c0200154611100526016600160c052602060c0200154611120526017600160c052602060c0200154611140526018600160c052602060c0200154611160526019600160c052602060c020015461118052601a600160c052602060c02001546111a052601b600160c052602060c02001546111c052601c600160c052602060c02001546111e052601d600160c052602060c020015461120052601e600160c052602060c020015461122052601f600160c052602060c020015461124052610460610dc052610dc051610e2052610420805160200180610dc051610e0001828460006004600a8704601201f1610c3257600080fd5b5050610dc051610e00015160206001820306601f8201039050610dc051610e0001610da08151610220818352015b83610da051101515610c7157610c8e565b6000610da0516020850101535b8151600101808352811415610c60575b505050506020610dc051610e00015160206001820306601f8201039050610dc0510101610dc052610dc051610e4052610d40805160200180610dc051610e0001828460006004600a8704601201f1610ce557600080fd5b5050610dc051610e00015160206001820306601f8201039050610dc051610e0001610da081516020818352015b83610da051101515610d2357610d40565b6000610da0516020850101535b8151600101808352811415610d12575b505050506020610dc051610e00015160206001820306601f8201039050610dc0510101610dc0527fce7a77a358682d6c81f71216fb7fb108b03bc8badbf67f5d131ba5363cbefb42610dc051610e00a16407735940006103a051141561100c576003805460018254011015610db457600080fd5b6001815401815550614000600354141561100b574261128052426112a05262015180610ddf57600080fd5b620151806112a05106611280511015610df757600080fd5b426112a05262015180610e0957600080fd5b620151806112a051066112805103620151804261128052426112a05262015180610e3257600080fd5b620151806112a05106611280511015610e4a57600080fd5b426112a05262015180610e5c57600080fd5b620151806112a051066112805103011015610e7657600080fd5b620151804261128052426112a05262015180610e9157600080fd5b620151806112a05106611280511015610ea957600080fd5b426112a05262015180610ebb57600080fd5b620151806112a05106611280510301611260526060611340602463806732896112c052611260516112e0526112dc6000305af1610ef757600080fd5b6113608051602001806113a0828460006004600a8704601201f1610f1a57600080fd5b5050610bc0516114605260406114205261142051611480526113a08051602001806114205161146001828460006004600a8704601201f1610f5a57600080fd5b505061142051611460015160206001820306601f8201039050611420516114600161140081516020818352015b8361140051101515610f9857610fb5565b6000611400516020850101535b8151600101808352811415610f87575b50505050602061142051611460015160206001820306601f8201039050611420510101611420527ffeaac92a4b34ca382b420f2dd3bacfa54fdbf8566e0b82a96c0f1292a2c267f061142051611460a160016004555b5b005b63845980e8600051141561103457341561102757600080fd5b60045460005260206000f3005b60006000fd5b6101f261122c036101f26000396101f261122c036000f3";

  public static final String FUNC_TO_LITTLE_ENDIAN_64 = "to_little_endian_64";

  public static final String FUNC_GET_DEPOSIT_ROOT = "get_deposit_root";

  public static final String FUNC_DEPOSIT = "deposit";

  public static final String FUNC_CHAINSTARTED = "chainStarted";

  public static final Event DEPOSIT_EVENT =
      new Event(
          "Deposit",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bytes32>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference.StaticArrayTypeReference<StaticArray<Bytes32>>(32) {}));

  public static final Event ETH2GENESIS_EVENT =
      new Event(
          "Eth2Genesis",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}));;

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  @SuppressWarnings("unchecked")
  public List<DepositEventResponse> getDepositEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DEPOSIT_EVENT, transactionReceipt);
    ArrayList<DepositEventResponse> responses =
        new ArrayList<DepositEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      DepositEventResponse typedResponse = new DepositEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.deposit_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.merkle_tree_index =
          (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse.branch = (List<Bytes32>) eventValues.getNonIndexedValues().get(3).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  @SuppressWarnings("unchecked")
  public Flowable<DepositEventResponse> depositEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, DepositEventResponse>() {
              @Override
              public DepositEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(DEPOSIT_EVENT, log);
                DepositEventResponse typedResponse = new DepositEventResponse();
                typedResponse.log = log;
                typedResponse.deposit_root =
                    (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.data = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.merkle_tree_index =
                    (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.branch =
                    (List<Bytes32>) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<DepositEventResponse> depositEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DEPOSIT_EVENT));
    return depositEventFlowable(filter);
  }

  public List<Eth2GenesisEventResponse> getEth2GenesisEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ETH2GENESIS_EVENT, transactionReceipt);
    ArrayList<Eth2GenesisEventResponse> responses =
        new ArrayList<Eth2GenesisEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.deposit_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, Eth2GenesisEventResponse>() {
              @Override
              public Eth2GenesisEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ETH2GENESIS_EVENT, log);
                Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
                typedResponse.log = log;
                typedResponse.deposit_root =
                    (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ETH2GENESIS_EVENT));
    return eth2GenesisEventFlowable(filter);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<byte[]> to_little_endian_64(BigInteger value) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_TO_LITTLE_ENDIAN_64,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(value)),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<byte[]> get_deposit_root() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_ROOT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<TransactionReceipt> deposit(byte[] deposit_input, BigInteger weiValue) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_DEPOSIT,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(deposit_input)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function, weiValue);
  }

  @SuppressWarnings("rawtypes")
  public RemoteCall<Boolean> chainStarted() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_CHAINSTARTED,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
    return executeRemoteCallSingleValueReturn(function, Boolean.class);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(
        contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(
        contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(
        contractAddress, web3j, credentials, contractGasProvider);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(
        contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        "");
  }

  public static class DepositEventResponse {
    public Log log;

    public byte[] deposit_root;

    public byte[] data;

    public byte[] merkle_tree_index;

    public List<Bytes32> branch;
  }

  public static class Eth2GenesisEventResponse {
    public Log log;

    public byte[] deposit_root;

    public byte[] time;
  }
}
