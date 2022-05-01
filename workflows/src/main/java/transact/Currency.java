package transact;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.money.MoneyUtilities;
import com.r3.corda.lib.tokens.selection.TokenQueryBy;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import kotlin.Unit;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.VaultService;
import net.corda.core.transactions.SignedTransaction;

import java.util.Collections;

public interface Currency {

    /**
     * Lets the node calling the flow issue some currency to a holder
     * Valid currencies are USD, AUD, GBP, BTC
     * You may add new Issuing flows to this interface to experiment
     */
    @StartableByRPC
    class IssueCash extends FlowLogic<SignedTransaction> {

        private final Party holder;
        private final Long amount;
        private final String currencyCode;

        /**
         * Issue
         * @param holder - the party who will receive the tokens
         * @param currencyCode - an ISO type code string
         * @param amount - amount
         */
        public IssueCash(Party holder, String currencyCode, Long amount) {
            this.holder = holder;
            this.amount = amount;
            this.currencyCode = currencyCode;

        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            final Party issuer = getOurIdentity();
            TokenType tokenType;

            switch (currencyCode) {
                case "USD":
                    // MoneyUtilities returns either a TokenType or Amount<TokenType> related to standard currencies
                    tokenType = MoneyUtilities.getUSD();
                    break;
                case "AUD":
                    tokenType = MoneyUtilities.getAUD();
                    break;
                case "GBP":
                    // FiatCurrency returns a TokenType from an ISO currency code
                    tokenType = FiatCurrency.getInstance(currencyCode);
                    break;
                case "BTC":
                    // DigitalCurrency returns a TokenType related to standard crypto/digital currencies
                    tokenType = DigitalCurrency.getInstance(currencyCode);
                    break;
                default:
                    throw new FlowException("unable to generate currency");
            }

            // The FungibleTokenBuilder allows quick and easy stepwise assembly of a token that can be split/merged
            FungibleToken tokens = new FungibleTokenBuilder()
                    .ofTokenType(tokenType)
                    .withAmount(amount)
                    .issuedBy(issuer)
                    .heldBy(holder)
                    .buildFungibleToken();

            return subFlow(new IssueTokens(Collections.singletonList(tokens)));
        }
    }
    @StartableByRPC
    class getBalance extends FlowLogic<String>{
        private final String currencyCode;

        public getBalance(String currencyCode){
            this.currencyCode = currencyCode;
        }


        @Override
        @Suspendable
        public String call() throws FlowException {

            VaultService vs = getServiceHub().getVaultService();
            TokenType tokenType;
            switch (currencyCode) {
                case "USD":
                    // MoneyUtilities returns either a TokenType or Amount<TokenType> related to standard currencies
                    tokenType = MoneyUtilities.getUSD();
                    break;
                case "AUD":
                    tokenType = MoneyUtilities.getAUD();
                    break;
                case "GBP":
                    // FiatCurrency returns a TokenType from an ISO currency code
                    tokenType = FiatCurrency.getInstance(currencyCode);
                    break;
                case "BTC":
                    // DigitalCurrency returns a TokenType related to standard crypto/digital currencies
                    tokenType = DigitalCurrency.getInstance(currencyCode);
                    break;
                default:
                    throw new FlowException("unable to generate currency");
            }


            TokenQueryBy tokenQueryBy = new TokenQueryBy(
                    null,
                    it -> it.getState().getData().getAmount().toDecimal().stripTrailingZeros().scale() > 0);

            Amount<TokenType> totalamount  = QueryUtilities.tokenBalance(vs,tokenType);

            return "\n holder has "+totalamount ;
        }
    }
    @InitiatingFlow
    @StartableByRPC
    class MoveCash extends FlowLogic<SignedTransaction> {

        public MoveCash(String currencyCode, Long amount, Party holder) {

            this.holder = holder;
            long l = amount*100;
            this.amount = l;
            this.currencyCode = currencyCode;

        }

        private final Party holder;
        private final Long amount;
        private final String currencyCode;
        @Override
        public SignedTransaction call() throws FlowException {
            VaultService vs = getServiceHub().getVaultService();
            TokenType tokenType;
            switch (currencyCode) {
                case "USD":
                    // MoneyUtilities returns either a TokenType or Amount<TokenType> related to standard currencies
                    tokenType = MoneyUtilities.getUSD();
                    break;
                case "AUD":
                    tokenType = MoneyUtilities.getAUD();
                    break;
                case "GBP":
                    // FiatCurrency returns a TokenType from an ISO currency code
                    tokenType = FiatCurrency.getInstance(currencyCode);
                    break;
                case "BTC":
                    // DigitalCurrency returns a TokenType related to standard crypto/digital currencies
                    tokenType = DigitalCurrency.getInstance(currencyCode);
                    break;
                default:
                    throw new FlowException("unable to generate currency");
            }
            TokenQueryBy tokenQueryBy = new TokenQueryBy(
                    null,
                    it -> it.getState().getData().getAmount().toDecimal().stripTrailingZeros().scale() > 0);

            Amount<TokenType> sendamount  = new Amount<>( amount, tokenType) ;


            return subFlow(new MoveFungibleTokens(sendamount,holder));
        }

    }
    @InitiatedBy(MoveCash.class)
    public static class MoveResponder extends FlowLogic<Unit> {

        private FlowSession counterSession;

        public MoveResponder(FlowSession counterSession) {
            this.counterSession = counterSession;
        }

        @Suspendable
        @Override
        public Unit call() throws FlowException {
            // Simply use the MoveFungibleTokensHandler as the responding flow
            return subFlow(new MoveFungibleTokensHandler(counterSession));
        }
    }
}
