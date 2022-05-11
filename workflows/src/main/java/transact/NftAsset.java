package transact;

import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilities;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.NonFungibleTokenBuilder;
import org.jetbrains.annotations.NotNull;
import co.paralleluniverse.fibers.Suspendable;
import kotlin.Pair;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import  states.AssetState;

public interface NftAsset{
/**
 * Flow to create and issue house tokens. Token SDK provides some in-built flows which could be called to Create and Issue tokens.
 * This flow should be called by the issuer of the token. The constructor takes the owner and other properties of the house as
 * input parameters, it first creates the house token onto the issuer's ledger and then issues it to the owner.
*/
@StartableByRPC
public class createNftAsset extends FlowLogic<String> {

    private final String assetName;
    private final Amount<Currency> valuation;
    private final String additionInfo;
    private final String assetDescription;

    public createNftAsset( String assetName, Amount<Currency> valuation,
                                      String assetDescription,
                                        String additionInfo) {
        this.assetName= assetName;
        this.valuation = valuation;
        this.additionInfo = additionInfo;
        this.assetDescription= assetDescription;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {

        // Obtain a reference to a notary we wish to use.
        /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)*/
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        /* Get a reference of own identity */
        Party issuer = getOurIdentity();

        /* Construct the output state */
        UniqueIdentifier uuid = UniqueIdentifier.Companion.fromString(UUID.randomUUID().toString());
        final AssetState houseState = new AssetState( assetName ,assetDescription,uuid, ImmutableList.of(issuer),
                valuation,  additionInfo);

        /* Create an instance of TransactionState using the houseState token and the notary */
        TransactionState<AssetState> transactionState = new TransactionState<>(houseState, notary);

        /* Create the house token. Token SDK provides the CreateEvolvableTokens flow which could be called to create an
        evolvable token in the ledger.*/
        subFlow(new CreateEvolvableTokens(transactionState));

        /* Create an instance of the non-fungible house token with the owner as the token holder.
        * Notice the TokenPointer is used as the TokenType, since EvolvableTokenType is not TokenType, but is
        * a LinearState. This is done to separate the state info from the token so that the state can evolve independently.
        * */
        NonFungibleToken houseToken = new NonFungibleTokenBuilder()
                .ofTokenType(houseState.toPointer())
                .issuedBy(issuer)
                .heldBy(issuer)
                .buildNonFungibleToken();

        /* Issue the house token by calling the IssueTokens flow provided with the TokenSDK */
        SignedTransaction stx = subFlow(new IssueTokens(ImmutableList.of(houseToken)));
        return "\nThe non-fungible house token is created with UUID: "+ uuid +". (This is what you will use in next step)"
                +"\nTransaction ID: "+stx.getId();

    }
}
@InitiatingFlow
@StartableByRPC
public class sellNftAsset extends FlowLogic<String> {

    private final String houseId;
    private final Party buyer;

    public sellNftAsset(String houseId, Party buyer) {
        this.houseId = houseId;
        this.buyer = buyer;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {

        // Obtain a reference to a notary we wish to use.
        /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)*/
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        /* Get the UUID from the houseId parameter */
        UUID uuid = UUID.fromString(houseId);

        /* Fetch the house state from the vault using the vault query */
        QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                null, ImmutableList.of(uuid), null, Vault.StateStatus.UNCONSUMED);

        StateAndRef<AssetState> houseStateAndRef = getServiceHub().getVaultService().
                queryBy(AssetState.class, queryCriteria).getStates().get(0);

        AssetState houseState = houseStateAndRef.getState().getData();

        /* Build the transaction builder */
        TransactionBuilder txBuilder = new TransactionBuilder(notary);

        /* Create a move token proposal for the house token using the helper function provided by Token SDK. This would
        create the movement proposal and would be committed in the ledgers of parties once the transaction in finalized */
        MoveTokensUtilities.addMoveNonFungibleTokens(txBuilder, getServiceHub(), houseState.toPointer(), buyer);

        /* Initiate a flow session with the buyer to send the house valuation and transfer of the fiat currency */
        FlowSession buyerSession = initiateFlow(buyer);

        /* Send the house valuation to the buyer */
        buyerSession.send(houseState.getValuation());

        /* Receive inputStatesAndRef for the fiat currency exchange from the buyer, these would be inputs to the fiat currency exchange transaction */
        List<StateAndRef<FungibleToken>> inputs = subFlow(new ReceiveStateAndRefFlow<>(buyerSession));

        /* Receive output for the fiat currency from the buyer, this would contain the transferred amount from buyer to yourself */
        List<FungibleToken> moneyReceived = buyerSession.receive(List.class).unwrap(value -> value);

        /* Create a fiat currency proposal for the house token using the helper function provided by Token SDK */
        MoveTokensUtilities.addMoveTokens(txBuilder, inputs, moneyReceived);
        /* Sign the transaction */
        SignedTransaction initialSignedTrnx = getServiceHub().signInitialTransaction(txBuilder, getOurIdentity().getOwningKey());

        /* Call the CollectSignaturesFlow to receive signature of the buyer */
        SignedTransaction signedTransaction = subFlow(new CollectSignaturesFlow(initialSignedTrnx, ImmutableList.of(buyerSession)));

        /* Call finality flow to notarise the transaction */
        SignedTransaction stx = subFlow(new FinalityFlow(signedTransaction, ImmutableList.of(buyerSession)));

        /* Distribution list is a list of identities that should receive updates. For this mechanism to behave correctly we call the UpdateDistributionListFlow flow */
        subFlow(new UpdateDistributionListFlow(stx));

        return "\nThe house is sold to "+ this.buyer.getName().getOrganisation() + "\nTransaction ID: "
                + stx.getId();
    }
    


}
@InitiatedBy(sellNftAsset.class)
public class sellNftResponder extends FlowLogic<SignedTransaction> {

    private final FlowSession counterpartySession;

    public sellNftResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {

        /* Receive the valuation of the house */
        Amount<Currency> price =  counterpartySession.receive(Amount.class).unwrap(amount -> amount);

        /* Create instance of the fiat currency token amount */
        Amount<TokenType> priceToken = new Amount<>(price.getQuantity(), FiatCurrency.Companion.getInstance(price.getToken().getCurrencyCode()));


        /* Generate the move proposal, it returns the input-output pair for the fiat currency transfer, which we need to
        send to the Initiator */
        PartyAndAmount<TokenType> partyAndAmount = new PartyAndAmount<>(counterpartySession.getCounterparty(), priceToken);
        Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> inputsAndOutputs = new DatabaseTokenSelection(getServiceHub())
                // here we are generating input and output states which send the correct amount to the seller, and any change back to buyer
                .generateMove(Collections.singletonList(new Pair<>(counterpartySession.getCounterparty(), priceToken)), getOurIdentity());

        /* Call SendStateAndRefFlow to send the inputs to the Initiator */
        subFlow(new SendStateAndRefFlow(counterpartySession, inputsAndOutputs.getFirst()));

        /* Send the output generated from the fiat currency move proposal to the initiator */
        counterpartySession.send(inputsAndOutputs.getSecond());
        subFlow(new SignTransactionFlow(counterpartySession) {
            @Override
            protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                // Custom Logic to validate transaction.
            }
        });
        return subFlow(new ReceiveFinalityFlow(counterpartySession));
    }
}
}