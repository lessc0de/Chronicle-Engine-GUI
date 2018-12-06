package musiverification;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.wire.WireType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static net.openhft.chronicle.engine.Chassis.assetTree;
import static net.openhft.chronicle.engine.Chassis.resetChassis;

public class KeySubscriberTest
{

    private static int expectedNoOfEvents = 2;
    private static int maxWait = 5;
    private static int waitCounter = 0;

    private static Map<String, String> _stringStringMap;
    private static String _mapName = "/chronicleMapString";
    private static String _mapArgs = "putReturnsNull=false";
    private static AssetTree _clientAssetTree;

    @Before
    public void setUp() throws IOException
    {
        resetChassis();

        AssetTree serverAssetTree = new VanillaAssetTree(1).forServer(true);
        //The following line doesn't add anything and breaks subscriptions
//        serverAssetTree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore", VanillaMapView::new, KeyValueStore.class);
//        serverAssetTree.root().addLeafRule(KeyValueStore.class, "use Chronicle Map", (context, asset) ->
//                new ChronicleMapKeyValueStore(context.basePath(OS.TARGET).entries(20).averageValueSize(10_000), asset));

        TCPRegistry.createServerSocketChannelFor("SubscriptionModelOnKeyTest.port");
        ServerEndpoint serverEndpoint = new ServerEndpoint("SubscriptionModelOnKeyTest.port", serverAssetTree, "cluster");

        _clientAssetTree = new VanillaAssetTree(89).forRemoteAccess("SubscriptionModelOnKeyTest" +
                ".port", WireType.BINARY, Throwable::printStackTrace);

        _stringStringMap = _clientAssetTree.acquireMap(_mapName + "?" + _mapArgs, String.class, String.class);

        //FIXME why are we required to make a call first? -
        // to make it a map for the key subscription.
        _stringStringMap.size();
    }

    @After
    public void tearDown()
    {
        waitCounter = 0;
        assetTree().close();
    }

    @Test
    public void testSubscriptionOnKey() throws InterruptedException
    {
        String testKey = "Key-sub-1";
        String keyUri = _mapName + "/" + testKey + "?bootstrap=false";

        AtomicInteger atomicInteger = new AtomicInteger(0);

        _clientAssetTree.registerSubscriber(keyUri, String.class, m -> {
            int eventNo = atomicInteger.incrementAndGet();
            System.out.println("KeySubscriber (#" + eventNo + ") " + m);
        });

        Jvm.pause(200);

        _stringStringMap.put(testKey, "Val1");
        _stringStringMap.put(testKey, "Val2");

        waitForEvents(atomicInteger);

        Assert.assertEquals(2, atomicInteger.get());
    }

    @Test
    public void testSubscriptionOnMap() throws InterruptedException
    {
        String testKey = "Key-sub-1";

        AtomicInteger atomicInteger = new AtomicInteger(0);

        _clientAssetTree.registerSubscriber(_mapName, String.class, m -> {
            int eventNo = atomicInteger.incrementAndGet();
            System.out.println("KeySubscriber (#" + eventNo + ") " + m);
        });

        Jvm.pause(200);

        _stringStringMap.put(testKey, "Val1");
        _stringStringMap.put(testKey, "Val2");

        waitForEvents(atomicInteger);

        Assert.assertEquals(2, atomicInteger.get());
    }

    private void waitForEvents(AtomicInteger atomicInteger) throws InterruptedException
    {
        while (atomicInteger.get() != expectedNoOfEvents && waitCounter != maxWait)
        {
            System.out.println("Waiting for events...");
            waitCounter++;
            Jvm.pause(1000);
        }
    }
}