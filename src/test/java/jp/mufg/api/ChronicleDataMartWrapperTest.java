package jp.mufg.api;

import jp.mufg.api.util.MetaData;
import jp.mufg.api.util.PrintAll;
import net.openhft.chronicle.Chronicle;
import net.openhft.chronicle.ChronicleQueueBuilder;
import net.openhft.chronicle.tools.ChronicleTools;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static jp.mufg.api.Util.newQuote;
import static jp.mufg.api.Util.newSubscription;
import static net.openhft.chronicle.core.Jvm.pause;
import static net.openhft.lang.model.DataValueClasses.newInstance;
import static org.easymock.EasyMock.*;

public class ChronicleDataMartWrapperTest {
    @NotNull
    static Chronicle createChronicle(String name) throws IOException {
        String basePath = name + "-" + System.nanoTime();
        ChronicleTools.deleteDirOnExit(basePath);

        Chronicle chronicle = ChronicleQueueBuilder.vanilla(basePath).build();
        MetaData.setId(chronicle, (byte) 111);
        return chronicle;
    }

    // TODO Why should calculate be called once?
    @Test
    @Ignore("TODO Clarify if this should work")
    public void testSingleThread() throws IOException, InterruptedException {
        Chronicle chronicle = createChronicle("testSingleThread");

        Map<String, MarketDataUpdate> marketDataMap = new HashMap<>();
        Calculator calculator = createMock(Calculator.class);
        calculator.calculate();
        replay(calculator);

        FilteringDataMart fdm = new FilteringDataMart("target",
                marketDataMap, calculator);
        DataMartWrapper dataMartWrapper = new ChronicleDataMartWrapper(
                chronicle,
                PrintAll.of(DirectDataMart.class, fdm));

//        ChronicleDataMart chronicleDataMart2 = new ChronicleDataMart(chronicle,
//                PrintAll.of(DataMart.class, new FilteringDataMart("target", marketDataMap, calculator)));

        DataMart writer = new ChronicleDataMartWriter(chronicle);
        writer.addSubscription(newSubscription("target", "one", "source", "exchange", "instrument2"));
        writer.addSubscription(newSubscription("target", "two", "source", "exchange", "instrument3"));
        writer.addSubscription(newSubscription("target", "three", "source", "exchange", "instrument"));
        MarketDataUpdate update = newInstance(MarketDataUpdate.class);

        writer.onUpdate(newQuote(update, "source", "exchange", "instrument", 10, 21, 10, 20));
        writer.onUpdate(newQuote(update, "source", "exchange", "instrument2", 13, 22, 10, 20));
        writer.onUpdate(newQuote(update, "source", "exchangeX", "instrument3", 16, 23, 10, 20));

        for (int i = 0; i < 3; i++) {
            while (dataMartWrapper.runOnce()/* |
                    chronicleDataMart2.runOnce()*/) {
            }
            if (!dataMartWrapper.onIdle())
                pause(10);
//            chronicleDataMart2.onIdle();
        }
        verify(calculator);
        chronicle.close();
    }
}