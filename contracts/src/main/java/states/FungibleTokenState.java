package states;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import  contracts.FungibleTokenContract;
import javafx.scene.shape.CubicCurve;

import org.jetbrains.annotations.NotNull;

import java.util.Currency;
import java.util.List;
import java.util.Objects;

@BelongsToContract(FungibleTokenContract.class)
public class FungibleTokenState extends EvolvableTokenType {

    private final String assetName;
    private final Party maintainer;
    private final UniqueIdentifier uniqueIdentifier;
    private final String symbol;
    private final int fractionDigits;
    private final Amount<Currency> value;

    public FungibleTokenState(String assetName, Party maintainer,
                                   UniqueIdentifier uniqueIdentifier, Amount<Currency>value, int fractionDigits, String symbol) {
        this.assetName= assetName;
        this.maintainer = maintainer;
        this.uniqueIdentifier = uniqueIdentifier;
        this.value=value;
        this.symbol = symbol;
        this.fractionDigits = fractionDigits;
    }

        public Amount<Currency> getValue() {
        return value;
    }

        public String getAssetName() {
        return assetName;
    }

    public String getSymbol() {
        return symbol;
    }

    public Party getMaintainer() {
        return maintainer;
    }

    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.of(maintainer);
    }

    @Override
    public int getFractionDigits() {
        return this.fractionDigits;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return this.uniqueIdentifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FungibleTokenState that = (FungibleTokenState) o;
        return getFractionDigits() == that.getFractionDigits() &&
                getAssetName() == (that.getAssetName()) &&
                getMaintainer().equals(that.getMaintainer()) &&
                uniqueIdentifier.equals(that.uniqueIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAssetName(), getMaintainer(), uniqueIdentifier, getFractionDigits());
    }
}
