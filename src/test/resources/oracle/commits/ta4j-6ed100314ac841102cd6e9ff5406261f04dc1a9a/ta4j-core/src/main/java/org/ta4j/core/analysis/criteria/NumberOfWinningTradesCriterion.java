package org.ta4j.core.analysis.criteria;

import org.ta4j.core.PriceType;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

/**
 * Number of winning trades criterion.
 */
public class NumberOfWinningTradesCriterion extends AbstractBacktestingCriterion {

    public NumberOfWinningTradesCriterion(PriceType priceType) {
        super(priceType);
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
         long numberOfLosingTrades = tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .filter(trade -> isWinningTrade(series, trade)).count();
         return PrecisionNum.valueOf(numberOfLosingTrades);
    }

    private boolean isWinningTrade(TimeSeries series, Trade trade) {
        Num exitPrice = getPrice(series, trade.getExit());
        Num entryPrice = getPrice(series, trade.getEntry());

        Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
        return profit.isGreaterThan(PrecisionNum.valueOf(0));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return series.numOf(1);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}