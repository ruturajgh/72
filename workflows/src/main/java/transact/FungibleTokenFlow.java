package transact;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;

import co.paralleluniverse.fibers.Suspendable;
import kotlin.Unit;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import  states.FungibleTokenState;

/**
 * Create,Issue,Move,Redeem token flows for a house asset on ledger
 * This is all-in-one implementation style.
 */
public interface FungibleTokenFlow {

    /**
     * Create Fungible Token for a house asset on ledger
     */
    @StartableByRPC
    public static class CreateToken extends FlowLogic<SignedTransaction> {

        // valuation property of a house can change hence we are considering house as a evolvable asset
        private final String assetName;
        private final String assetDescription;
        private final String symbol;

        public CreateToken(String assetName,String assetDescription ,String symbol ) {
            this.assetName = assetName ;
            this.assetDescription= assetDescription;
            this.symbol = symbol;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to a notary we wish to use.
            /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)*/
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB"));
            //create token type
            FungibleTokenState evolvableTokenType = new FungibleTokenState(assetName,assetDescription,getOurIdentity(),
                    new UniqueIdentifier(), null, 0, this.symbol);

            //wrap it with transaction state specifying the notary
            TransactionState<FungibleTokenState> transactionState = new TransactionState<>(evolvableTokenType, notary);

            //call built in sub flow CreateEvolvableTokens. This can be called via rpc or in unit testing
            return subFlow(new CreateEvolvableTokens(transactionState));
        }
    }

    /**
     *  Issue Fungible Token against an evolvable house asset on ledger
     */
    @StartableByRPC
    public static class IssueTokenProperty extends FlowLogic<SignedTransaction>{
        private final String symbol;
        private final int quantity;
        public IssueTokenProperty(String symbol, int quantity, Amount<Currency> value) {
            this.symbol = symbol;
            this.quantity = quantity;

        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get house states on ledger with uuid as input tokenId
            StateAndRef<FungibleTokenState> stateAndRef = getServiceHub().getVaultService().
                    queryBy(FungibleTokenState.class).getStates().stream()
                    .filter(sf->sf.getState().getData().getSymbol().equals(symbol)).findAny()
                    .orElseThrow(()-> new IllegalArgumentException("FungibleHouseTokenState symbol=\""+symbol+"\" not found from vault"));

            //get the RealEstateEvolvableTokenType object
            FungibleTokenState evolvableTokenType = stateAndRef.getState().getData();

            //create fungible token for the house token type
            FungibleToken fungibleToken = new FungibleTokenBuilder()
                    .ofTokenType(evolvableTokenType.toPointer(FungibleTokenState.class)) // get the token pointer
                    .issuedBy(getOurIdentity())
                    .heldBy(getOurIdentity())
                    .withAmount(quantity)
                    .buildFungibleToken();

            //use built in flow for issuing tokens on ledger
            return subFlow(new IssueTokens(ImmutableList.of(fungibleToken)));
        }
    }

    /**
     *  Move created fungible tokens to other party
     */
    @StartableByRPC
    @InitiatingFlow
    public static class MoveHouseTokenFlow extends FlowLogic<SignedTransaction>{
        private final String symbol;
        private final Party holder;
        private final int quantity;

        public MoveHouseTokenFlow(String symbol, Party holder, int quantity) {
            this.symbol = symbol;
            this.holder = holder;
            this.quantity = quantity;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            //get house states on ledger with uuid as input tokenId
            StateAndRef<FungibleTokenState> stateAndRef = getServiceHub().getVaultService().
                    queryBy(FungibleTokenState.class).getStates().stream()
                    .filter(sf->sf.getState().getData().getSymbol().equals(symbol)).findAny()
                    .orElseThrow(()-> new IllegalArgumentException("FungibleHouseTokenState=\""+symbol+"\" not found from vault"));

            //get the RealEstateEvolvableTokenType object
            FungibleTokenState tokenstate = stateAndRef.getState().getData();

            /*  specify how much amount to transfer to which holder
             *  Note: we use a pointer of tokenstate because it of type EvolvableTokenType
             */
            Amount<TokenType> amount = new Amount<>(quantity, tokenstate.toPointer(FungibleTokenState.class));
            //PartyAndAmount partyAndAmount = new PartyAndAmount(holder, amount);

            //use built in flow to move fungible tokens to holder
            return subFlow(new MoveFungibleTokens(amount,holder));
        }
    }

    @InitiatedBy(MoveHouseTokenFlow.class)
    public static class MoveEvolvableFungibleTokenFlowResponder extends FlowLogic<Unit>{

        private FlowSession counterSession;

        public MoveEvolvableFungibleTokenFlowResponder(FlowSession counterSession) {
            this.counterSession = counterSession;
        }

        @Suspendable
        @Override
        public Unit call() throws FlowException {
            // Simply use the MoveFungibleTokensHandler as the responding flow
            return subFlow(new MoveFungibleTokensHandler(counterSession));
        }
    }
    @InitiatingFlow
    @StartableByRPC
    public static class GetTokenBalance extends FlowLogic<String> {
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String symbol;

        public GetTokenBalance(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            //get a set of the RealEstateEvolvableTokenType object on ledger with uuid as input tokenId
            Set<FungibleTokenState> evolvableTokenTypeSet = getServiceHub().getVaultService().
                    queryBy(FungibleTokenState.class).getStates().stream()
                    .filter(sf->sf.getState().getData().getSymbol().equals(symbol)).map(StateAndRef::getState)
                    .map(TransactionState::getData).collect(Collectors.toSet());
            if (evolvableTokenTypeSet.isEmpty()){
                throw new IllegalArgumentException("FungibleHouseTokenState symbol=\""+symbol+"\" not found from vault");
            }

            // Save the result
            String result="";

            // Technically the set will only have one element, because we are query by symbol.
            for (FungibleTokenState evolvableTokenType : evolvableTokenTypeSet){
                //get the pointer pointer to the house
                TokenPointer<FungibleTokenState> tokenPointer = evolvableTokenType.toPointer(FungibleTokenState.class);
                //query balance or each different Token
                Amount<TokenType> amount = QueryUtilities.tokenBalance(getServiceHub().getVaultService(), tokenPointer);
                result += "\nYou currently have "+ amount.getQuantity()+ " " + symbol + " Tokens issued by "
                        +evolvableTokenType.getMaintainer().getName().getOrganisation()+"\n";
            }
            return result;
        }
    }

}

