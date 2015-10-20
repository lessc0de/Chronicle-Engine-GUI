package musiverification;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.pool.ClassAliasPool;
import net.openhft.chronicle.engine.api.EngineReplication;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.fs.ChronicleMapGroupFS;
import net.openhft.chronicle.engine.fs.FilePerKeyGroupFS;
import net.openhft.chronicle.engine.map.CMap2EngineReplicator;
import net.openhft.chronicle.engine.map.ChronicleMapKeyValueStore;
import net.openhft.chronicle.engine.map.VanillaMapView;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

/**
 * Based on the test, net.openhft.chronicle.engine.ReplicationTest2Way, created by Rob Austin.
 */

public class ReplicationTest2Way {
    public static final WireType WIRE_TYPE = WireType.TEXT;
    public static final String NAME = "/ChMaps/test";
    public static ServerEndpoint serverEndpoint1;
    public static ServerEndpoint serverEndpoint2;
    private static AssetTree tree1;
    private static AssetTree tree2;

    public static final String keyMap1 = "keyMap1";
    public static final String valueMap1 = "valueMap1";

    public static final String keyMap2 = "keyMap2";
    public static final String valueMap2 = "valueMap2";

    @BeforeClass
    public static void before() throws IOException {
        YamlLogging.clientWrites = true;
        YamlLogging.clientReads = true;

        ClassAliasPool.CLASS_ALIASES.addAlias(ChronicleMapGroupFS.class);
        ClassAliasPool.CLASS_ALIASES.addAlias(FilePerKeyGroupFS.class);
        //Delete any files from the last run
        Files.deleteIfExists(Paths.get(OS.TARGET, NAME));

        TCPRegistry.createServerSocketChannelFor("host.port1", "host.port2");

        createServer1();
        createServer2();
    }

    @AfterClass
    public static void after() throws IOException {
        closeServer1();
        closeServer2();

        TcpChannelHub.closeAllHubs();
        TCPRegistry.reset();
    }

    private static void createServer1()
    {
        tree1 = create(1, WIRE_TYPE, "clusterTwo");
        serverEndpoint1 = new ServerEndpoint("host.port1", tree1, WIRE_TYPE);
    }

    private static void createServer2()
    {
        tree2 = create(2, WIRE_TYPE, "clusterTwo");
        serverEndpoint2 = new ServerEndpoint("host.port2", tree2, WIRE_TYPE);
    }

    private static void closeServer1()
    {
        if (serverEndpoint1 != null)
            serverEndpoint1.close();

        if (tree1 != null)
            tree1.close();
    }

    private static void closeServer2()
    {
        if (serverEndpoint2 != null)
            serverEndpoint2.close();

        if (tree2 != null)
            tree2.close();
    }

    private static void restartServer1()
    {
        closeServer1();
        Jvm.pause(500);
        createServer1();
    }

    private static void restartServer2()
    {
        closeServer2();
        Jvm.pause(500);
        createServer2();
    }

    @NotNull
    private static AssetTree create(final int hostId, Function<Bytes, Wire> writeType, final String clusterTwo) {
        AssetTree tree = new VanillaAssetTree((byte) hostId)
                .forTesting()
                .withConfig(resourcesDir() + "/cmkvst", OS.TARGET + "/" + hostId);

        tree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore",
                VanillaMapView::new,
                KeyValueStore.class);
        tree.root().addLeafRule(EngineReplication.class, "Engine replication holder",
                CMap2EngineReplicator::new);
        tree.root().addLeafRule(KeyValueStore.class, "KVS is Chronicle Map", (context, asset) ->
                new ChronicleMapKeyValueStore(context.wireType(writeType).cluster(clusterTwo),
                        asset));


        return tree;
    }

    @NotNull
    public static String resourcesDir() {
        String path = ReplicationTest2Way.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path == null)
            return ".";

        return new File(path).getParentFile().getParentFile() + "/src/test/resources";
    }

    /**
     * Test that values are replicated (bootstrapped) when first connected when only one kvp is put in each map.
     */
    @Test
    public void testBootstrapOneKvPPerMapFirstBootstrap() throws InterruptedException {

        ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String.class);
        ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String.class);

        testInitialBootStrap(map1, map2);
    }

    /**
     * Test that values are replicated (bootstrapped) when first connected when TWO kvps are put in each map.
     */
    @Test
    public void testBootstrapTwoKvPPerMapFirstBootstrap() throws InterruptedException {

        ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String.class);
        assertNotNull(map1);

        String key2Map1 = "Key2Map1";
        String value2Map1 = "Value2Map1";

        map1.put(keyMap1, valueMap1);
        map1.put(key2Map1, value2Map1);

        //Replicated
        ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String.class);
        assertNotNull(map2);

        String key2Map2 = "Key2Map21";
        String value2Map2 = "Value2Map2";

        map2.put(keyMap2, valueMap2);
        map2.put(key2Map2, value2Map2);

        //Both maps should have 4 kvps
        for (int i = 1; i <= 50; i++) {
            if (map1.size() == 4 && map2.size() == 4)
                break;

            Jvm.pause(200);
        }

        for (Map m : new Map[]{map1, map2}) {
            Assert.assertEquals(4, m.size());
        }

        Assert.assertEquals(valueMap2, map1.get(keyMap2)); //Map1 contains KvP put in Map2
        Assert.assertEquals(value2Map2, map1.get(key2Map2));
        Assert.assertEquals(valueMap1, map2.get(keyMap1)); //Map2 contains KvP put in Map1
        Assert.assertEquals(value2Map1, map2.get(key2Map1));
    }

    /**
     * Test that maps are replicated after first connection and that the primary map is bootstrapped when restarted.
     */
    @Test
    public void testBootstrapOneKvPPerMapPrimaryRestart() throws InterruptedException {

        ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String.class);
        ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String.class);

        testInitialBootStrap(map1, map2);

        //Restart primary
        restartServer1();

        //re-create reference to map1
        map1 = tree1.acquireMap(NAME, String.class, String
                .class);
        assertNotNull(map1);

        Jvm.pause(500);

        //Test that map1 has bootstrapped after restart and contains both KvPs
        Assert.assertEquals(2, map1.size());
        Assert.assertEquals(valueMap1, map1.get(keyMap1));
        Assert.assertEquals(valueMap2, map1.get(keyMap2));
    }

    /**
     * Test that maps are replicated after first connection and that the secondary map is bootstrapped when restarted.
     */
    @Test
    public void testBootstrapOneKvPPerMapSecondaryRestart() throws InterruptedException
    {
        ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String.class);
        ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String.class);

        testInitialBootStrap(map1, map2);

        //Restart secondary
        restartServer2();

        //re-create reference to map1
        map2 = tree2.acquireMap(NAME, String.class, String
                .class);
        assertNotNull(map1);

        Jvm.pause(500);

        //Test that map1 has bootstrapped after restart and contains both KvPs
        Assert.assertEquals(2, map2.size());
        Assert.assertEquals(valueMap1, map2.get(keyMap1));
        Assert.assertEquals(valueMap2, map2.get(keyMap2));
    }

    /**
     * Test that values are replicated (bootstrapped) when first connected when only one kvp is put in each map.
     */
    private void testInitialBootStrap(ConcurrentMap<String, String> map1, ConcurrentMap<String, String> map2)
    {
        assertNotNull(map1);
        assertNotNull(map2);

        map1.put(keyMap1, valueMap1);
        map2.put(keyMap2, valueMap2);

        for (int i = 1; i <= 50; i++) {
            if (map1.size() == 2 && map2.size() == 2)
                break;

            Jvm.pause(200);
        }

        for (Map m : new Map[]{map1, map2}) {
            Assert.assertEquals(2, m.size());
        }

        Assert.assertEquals(valueMap2, map1.get(keyMap2)); //Map1 contains KvP put in Map2
        Assert.assertEquals(valueMap1, map2.get(keyMap1)); //Map2 contains KvP put in Map1
    }
}