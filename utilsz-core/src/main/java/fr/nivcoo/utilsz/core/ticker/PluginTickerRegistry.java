package fr.nivcoo.utilsz.core.ticker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginTickerRegistry implements PluginTicker {

    private final List<PluginTicker> tickers = new ArrayList<>();
    private boolean started;

    private PluginTickerRegistry() {
    }

    public static PluginTickerRegistry create() {
        return new PluginTickerRegistry();
    }

    public PluginTickerRegistry add(PluginTicker ticker) {
        if (ticker == null) return this;
        tickers.add(ticker);
        if (started) {
            ticker.start();
        }
        return this;
    }

    @Override
    public void start() {
        if (started) return;
        started = true;
        for (PluginTicker ticker : tickers) {
            ticker.start();
        }
    }

    @Override
    public void stop() {
        if (!started && tickers.isEmpty()) return;
        List<PluginTicker> reverse = new ArrayList<>(tickers);
        Collections.reverse(reverse);
        for (PluginTicker ticker : reverse) {
            ticker.stop();
        }
        started = false;
    }

    public void clear() {
        stop();
        tickers.clear();
    }
}
