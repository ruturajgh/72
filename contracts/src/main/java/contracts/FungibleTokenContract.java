package contracts;

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import  states.FungibleTokenState;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This doesn't do anything over and above the [EvolvableTokenContract].
 */
public class FungibleTokenContract extends EvolvableTokenContract implements Contract {

    public static final String CONTRACT_ID = "net.corda.samples.tokenizedhouse.contracts.HouseTokenStateContract";

    @Override
    public void additionalCreateChecks(@NotNull LedgerTransaction tx) {
        // Write contract validation logic to be performed while creation of token
    
    }

    @Override
    public void additionalUpdateChecks(@NotNull LedgerTransaction tx) {
        // Write contract validation logic to be performed while updation of token
    }
}
