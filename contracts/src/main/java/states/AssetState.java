package states;

import java.util.Currency;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;

import org.jetbrains.annotations.NotNull;

import  contracts.AssetContract;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;

@BelongsToContract(AssetContract.class)
public class AssetState extends EvolvableTokenType {

    private final String assetName;
    private final UniqueIdentifier linearId;
    private final List<Party> maintainers;
    private final Party issuer;
    private final int fractionDigits = 0;

    //Properties of House State. Some of these values may evolve over time.
    private final Amount<Currency> valuation;
    private String assetDescription;
    private final String additionInfo;
    

    public AssetState(String assetName, String assetDescription, UniqueIdentifier linearId, List<Party> maintainers, Amount<Currency> valuation, String additionInfo) {
       this.assetName = assetName;
        this.linearId = linearId;
        this.maintainers = maintainers; 
        this.valuation = valuation;
        this.assetDescription= assetDescription;
        this.additionInfo = additionInfo;
        issuer = maintainers.get(0);
    }

    public String getAssetName(){
        return assetName;
    }
    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

     public String getAssetDescription(){
         return assetDescription;
     }
    public String getAdditionInfo() {
        return additionInfo;
    }


    public Amount<Currency> getValuation() {
        return valuation;
    }

    public Party getIssuer() {
        return issuer;
    }

    @Override
    public int getFractionDigits() {
        return fractionDigits;
    }

    @NotNull
    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.copyOf(maintainers);
    }

    /* This method returns a TokenPointer by using the linear Id of the evolvable state */
    public TokenPointer<AssetState> toPointer(){
        LinearPointer<AssetState> linearPointer = new LinearPointer<>(linearId, AssetState.class);
        return new TokenPointer<>(linearPointer, fractionDigits);
    }
}