package net.openhft.chronicle.engine.gui.engine;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.SimpleEngineMain;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;

import java.io.IOException;

/**
 * @author Rob Austin.
 */
public class SimpleEngine {

    public static VanillaAssetTree createEngine()   {
        VanillaAssetTree assetTree = null;
        try {
            assetTree = SimpleEngineMain.tree();
        } catch (IOException e) {
            throw Jvm.rethrow(e);
        }

        MapView<String, String> mapView = assetTree.acquireMap("/my/demo/map", String.class, String.class);

        mapView.put("hello", "world");
        return assetTree;
    }

}