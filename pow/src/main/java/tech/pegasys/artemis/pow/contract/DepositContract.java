package tech.pegasys.artemis.pow.contract;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
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
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.3.0.
 */
public class DepositContract extends Contract {
    private static final String BINARY = "0x600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009e57600080fd5b6101406000601f818352015b600061014051602081106100bd57600080fd5b600060c052602060c020015460208261016001015260208101905061014051602081106100e957600080fd5b600060c052602060c020015460208261016001015260208101905080610160526101609050602060c0825160208401600060025af161012757600080fd5b60c0519050606051600161014051018060405190131561014657600080fd5b809190121561015457600080fd5b6020811061016157600080fd5b600060c052602060c02001555b81516001018083528114156100aa575b505061140756600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a0526380673289600051141561026b57602060046101403734156100b457600080fd5b67ffffffffffffffff6101405111156100cc57600080fd5b60006101605261014051610180526101a060006008818352015b6101605160086000811215610103578060000360020a820461010a565b8060020a82025b905090506101605260ff61018051166101c052610160516101c0516101605101101561013557600080fd5b6101c051610160510161016052610180517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8600081121561017e578060000360020a8204610185565b8060020a82025b90509050610180525b81516001018083528114156100e6575b505060186008602082066101e001602082840111156101bc57600080fd5b60208061020082610160600060046015f15050818152809050905090508051602001806102a0828460006004600a8704601201f16101f957600080fd5b50506102a05160206001820306601f82010390506103006102a0516008818352015b8261030051111561022b57610247565b6000610300516102c001535b815160010180835281141561021b575b50505060206102805260406102a0510160206001820306601f8201039050610280f3005b63c5f2892f60005114156103c357341561028457600080fd5b6000610140526002546101605261018060006020818352015b600160016101605116141561031e57600061018051602081106102bf57600080fd5b600160c052602060c02001546020826102200101526020810190506101405160208261022001015260208101905080610220526102209050602060c0825160208401600060025af161031057600080fd5b60c05190506101405261038c565b6000610140516020826101a0010152602081019050610180516020811061034457600080fd5b600060c052602060c02001546020826101a0010152602081019050806101a0526101a09050602060c0825160208401600060025af161038257600080fd5b60c0519050610140525b610160600261039a57600080fd5b60028151048152505b815160010180835281141561029d575b50506101405160005260206000f3005b63621fd13060005114156104995734156103dc57600080fd5b60606101c060246380673289610140526002546101605261015c6000305af161040457600080fd5b6101e0805160200180610260828460006004600a8704601201f161042757600080fd5b50506102605160206001820306601f82010390506102c0610260516008818352015b826102c051111561045957610475565b60006102c05161028001535b8151600101808352811415610449575b5050506020610240526040610260510160206001820306601f8201039050610240f3005b63c47e300d600051141561125657606060046101403760506004356004016101a03760306004356004013511156104cf57600080fd5b60406024356004016102203760206024356004013511156104ef57600080fd5b608060443560040161028037606060443560040135111561050f57600080fd5b63ffffffff6002541061052157600080fd5b60306101a0511461053157600080fd5b6020610220511461054157600080fd5b6060610280511461055157600080fd5b633b9aca00610340526103405161056757600080fd5b61034051340461032052633b9aca0061032051101561058557600080fd5b6060610440602463806732896103c052610320516103e0526103dc6000305af16105ae57600080fd5b610460805160200180610360828460006004600a8704601201f16105d157600080fd5b50506002546104a05260006104c0526104a05160016104a0510110156105f657600080fd5b60016104a051016104e05261050060006020818352015b600160016104e05116141561062157610674565b6104c060605160018251018060405190131561063c57600080fd5b809190121561064a57600080fd5b8152506104e0600261065b57600080fd5b60028151048152505b815160010180835281141561060d575b505060006101a06030806020846105e001018260208501600060046016f1505080518201915050600060106020820661056001602082840111156106b757600080fd5b60208061058082610520600060046015f15050818152809050905090506010806020846105e001018260208501600060046013f1505080518201915050806105e0526105e09050602060c0825160208401600060025af161071757600080fd5b60c051905061054052600060006040602082066106800161028051828401111561074057600080fd5b6060806106a0826020602088068803016102800160006004601bf1505081815280905090509050602060c0825160208401600060025af161078057600080fd5b60c051905060208261088001015260208101905060006040602060208206610740016102805182840111156107b457600080fd5b606080610760826020602088068803016102800160006004601bf150508181528090509050905060208060208461080001018260208501600060046015f15050805182019150506105205160208261080001015260208101905080610800526108009050602060c0825160208401600060025af161083157600080fd5b60c051905060208261088001015260208101905080610880526108809050602060c0825160208401600060025af161086857600080fd5b60c051905061066052600060006105405160208261092001015260208101905061022060208060208461092001018260208501600060046015f150508051820191505080610920526109209050602060c0825160208401600060025af16108ce57600080fd5b60c0519050602082610aa00101526020810190506000610360600880602084610a2001018260208501600060046012f150508051820191505060006018602082066109a0016020828401111561092357600080fd5b6020806109c082610520600060046015f1505081815280905090509050601880602084610a2001018260208501600060046014f150508051820191505061066051602082610a2001015260208101905080610a2052610a209050602060c0825160208401600060025af161099657600080fd5b60c0519050602082610aa001015260208101905080610aa052610aa09050602060c0825160208401600060025af16109cd57600080fd5b60c051905061090052610b2060006020818352015b6104c051610b20511215610a62576000610b205160208110610a0357600080fd5b600160c052602060c0200154602082610b4001015260208101905061090051602082610b4001015260208101905080610b4052610b409050602060c0825160208401600060025af1610a5457600080fd5b60c051905061090052610a67565b610a78565b5b81516001018083528114156109e2575b5050610900516104c05160208110610a8f57600080fd5b600160c052602060c02001556002805460018254011015610aaf57600080fd5b60018154018155506060610c4060246380673289610bc0526104a051610be052610bdc6000305af1610ae057600080fd5b610c60805160200180610ca0828460006004600a8704601201f1610b0357600080fd5b505060a0610d2052610d2051610d60526101a0805160200180610d2051610d6001828460006004600a8704601201f1610b3b57600080fd5b5050610d2051610d60015160206001820306601f8201039050610d2051610d6001610d0081516040818352015b83610d0051101515610b7957610b96565b6000610d00516020850101535b8151600101808352811415610b68575b505050506020610d2051610d60015160206001820306601f8201039050610d20510101610d2052610d2051610d8052610220805160200180610d2051610d6001828460006004600a8704601201f1610bed57600080fd5b5050610d2051610d60015160206001820306601f8201039050610d2051610d6001610d0081516020818352015b83610d0051101515610c2b57610c48565b6000610d00516020850101535b8151600101808352811415610c1a575b505050506020610d2051610d60015160206001820306601f8201039050610d20510101610d2052610d2051610da052610360805160200180610d2051610d6001828460006004600a8704601201f1610c9f57600080fd5b5050610d2051610d60015160206001820306601f8201039050610d2051610d6001610d0081516020818352015b83610d0051101515610cdd57610cfa565b6000610d00516020850101535b8151600101808352811415610ccc575b505050506020610d2051610d60015160206001820306601f8201039050610d20510101610d2052610d2051610dc052610280805160200180610d2051610d6001828460006004600a8704601201f1610d5157600080fd5b5050610d2051610d60015160206001820306601f8201039050610d2051610d6001610d0081516060818352015b83610d0051101515610d8f57610dac565b6000610d00516020850101535b8151600101808352811415610d7e575b505050506020610d2051610d60015160206001820306601f8201039050610d20510101610d2052610d2051610de052610ca0805160200180610d2051610d6001828460006004600a8704601201f1610e0357600080fd5b5050610d2051610d60015160206001820306601f8201039050610d2051610d6001610d0081516020818352015b83610d0051101515610e4157610e5e565b6000610d00516020850101535b8151600101808352811415610e30575b505050506020610d2051610d60015160206001820306601f8201039050610d20510101610d20527fdc5fc95703516abd38fa03c3737ff3b52dc52347055c8028460fdf5bbe2f12ce610d2051610d60a164077359400061032051101515611254576003805460018254011015610ed357600080fd5b60018154018155506201000060035414156112535742610e205242610e405262015180610eff57600080fd5b62015180610e405106610e20511015610f1757600080fd5b42610e405262015180610f2957600080fd5b62015180610e405106610e2051036202a30042610e205242610e405262015180610f5257600080fd5b62015180610e405106610e20511015610f6a57600080fd5b42610e405262015180610f7c57600080fd5b62015180610e405106610e205103011015610f9657600080fd5b6202a30042610e205242610e405262015180610fb157600080fd5b62015180610e405106610e20511015610fc957600080fd5b42610e405262015180610fdb57600080fd5b62015180610e405106610e20510301610e00526020610ee0600463c5f2892f610e8052610e9c6000305af161100f57600080fd5b610ee051610e60526060610f8060246380673289610f0052600254610f2052610f1c6000305af161103f57600080fd5b610fa0805160200180610fe0828460006004600a8704601201f161106257600080fd5b505060606110c06024638067328961104052610e00516110605261105c6000305af161108d57600080fd5b6110e0805160200180611120828460006004600a8704601201f16110b057600080fd5b5050610e60516111e05260606111a0526111a05161120052610fe08051602001806111a0516111e001828460006004600a8704601201f16110f057600080fd5b50506111a0516111e0015160206001820306601f82010390506111a0516111e00161118081516020818352015b836111805110151561112e5761114b565b6000611180516020850101535b815160010180835281141561111d575b5050505060206111a0516111e0015160206001820306601f82010390506111a05101016111a0526111a051611220526111208051602001806111a0516111e001828460006004600a8704601201f16111a257600080fd5b50506111a0516111e0015160206001820306601f82010390506111a0516111e00161118081516020818352015b83611180511015156111e0576111fd565b6000611180516020850101535b81516001018083528114156111cf575b5050505060206111a0516111e0015160206001820306601f82010390506111a05101016111a0527f08b71ef3f1b58f7a23ffb82e27f12f0888c8403f1ceb0ea7ea26b274e2189d4c6111a0516111e0a160016004555b5b005b63845980e8600051141561127c57341561126f57600080fd5b60045460005260206000f3005b60006000fd5b61018561140703610185600039610185611407036000f3\n";

    public static final String FUNC_TO_LITTLE_ENDIAN_64 = "to_little_endian_64";

    public static final String FUNC_GET_DEPOSIT_ROOT = "get_deposit_root";

    public static final String FUNC_GET_DEPOSIT_COUNT = "get_deposit_count";

    public static final String FUNC_DEPOSIT = "deposit";

    public static final String FUNC_CHAINSTARTED = "chainStarted";

    public static final Event DEPOSIT_EVENT = new Event("Deposit", 
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}));
    ;

    public static final Event ETH2GENESIS_EVENT = new Event("Eth2Genesis", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {}));
    ;

    @Deprecated
    protected DepositContract(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected DepositContract(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected DepositContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected DepositContract(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<DepositEventResponse> getDepositEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEPOSIT_EVENT, transactionReceipt);
        ArrayList<DepositEventResponse> responses = new ArrayList<DepositEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DepositEventResponse typedResponse = new DepositEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.withdrawal_credentials = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.signature = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
            typedResponse.merkle_tree_index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DepositEventResponse> depositEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DepositEventResponse>() {
            @Override
            public DepositEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEPOSIT_EVENT, log);
                DepositEventResponse typedResponse = new DepositEventResponse();
                typedResponse.log = log;
                typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.withdrawal_credentials = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.signature = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
                typedResponse.merkle_tree_index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DepositEventResponse> depositEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEPOSIT_EVENT));
        return depositEventFlowable(filter);
    }

    public List<Eth2GenesisEventResponse> getEth2GenesisEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ETH2GENESIS_EVENT, transactionReceipt);
        ArrayList<Eth2GenesisEventResponse> responses = new ArrayList<Eth2GenesisEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.deposit_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.deposit_count = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, Eth2GenesisEventResponse>() {
            @Override
            public Eth2GenesisEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(ETH2GENESIS_EVENT, log);
                Eth2GenesisEventResponse typedResponse = new Eth2GenesisEventResponse();
                typedResponse.log = log;
                typedResponse.deposit_root = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.deposit_count = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.time = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<Eth2GenesisEventResponse> eth2GenesisEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ETH2GENESIS_EVENT));
        return eth2GenesisEventFlowable(filter);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RemoteCall<byte[]> to_little_endian_64(BigInteger value) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_TO_LITTLE_ENDIAN_64, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(value)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RemoteCall<byte[]> get_deposit_root() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GET_DEPOSIT_ROOT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RemoteCall<byte[]> get_deposit_count() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GET_DEPOSIT_COUNT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RemoteCall<TransactionReceipt> deposit(byte[] pubkey, byte[] withdrawal_credentials, byte[] signature, BigInteger weiValue) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_DEPOSIT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(pubkey), 
                new org.web3j.abi.datatypes.DynamicBytes(withdrawal_credentials), 
                new org.web3j.abi.datatypes.DynamicBytes(signature)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RemoteCall<Boolean> chainStarted() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_CHAINSTARTED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    @Deprecated
    public static DepositContract load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new DepositContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static DepositContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new DepositContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static DepositContract load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new DepositContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static DepositContract load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new DepositContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<DepositContract> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(DepositContract.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<DepositContract> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(DepositContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<DepositContract> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(DepositContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<DepositContract> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(DepositContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class DepositEventResponse {
        public Log log;

        public byte[] pubkey;

        public byte[] withdrawal_credentials;

        public byte[] amount;

        public byte[] signature;

        public byte[] merkle_tree_index;
    }

    public static class Eth2GenesisEventResponse {
        public Log log;

        public byte[] deposit_root;

        public byte[] deposit_count;

        public byte[] time;
    }
}
