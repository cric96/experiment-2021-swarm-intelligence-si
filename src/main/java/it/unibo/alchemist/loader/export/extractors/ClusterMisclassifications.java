package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.loader.export.Extractor;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition;
import it.unibo.alchemist.model.interfaces.*;
import it.unibo.alchemist.model.interfaces.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An extractor that evaluates how many node are misclassified, namely they are marked to belong to a cluster
 * but they are far to a GaussianLayers
 */
public class ClusterMisclassifications implements Extractor<Double> {
    private final Double thr;
    private final List<String> columns;

    /**
     * @param thr used to define if a node is inside or outside a cluster (i.e. Gaussian Layer)
     * @param names the names of clusters exported in the CSV file
     */
    public ClusterMisclassifications(Double thr, String... names) {
        this.columns = Arrays.asList(names);
        this.thr = thr;
    }
    @NotNull
    @Override
    public List<String> getColumnNames() {
        return Collections.singletonList("errors");
    }

    @NotNull
    @Override
    public <T> Map<String, Double> extractData(@NotNull Environment<T, ?> environment, @Nullable Reaction<T> reaction, @NotNull Time time, long l) {
        if(columns.isEmpty()) {
            return Map.of("errors", 0.0);
        }
        final var unsafeEnv = (Environment<T, Euclidean2DPosition>) environment;
        // Extract all gaussian layers
        var layers = ClusterUtil.getGaussian(unsafeEnv, columns);
        // Verify, for each node, it they are misclassified => node has a cluster but no Gaussian layer is near to them.
        var wrongNodes = unsafeEnv.getNodes().stream()
                .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                .filter(node -> layers.stream()
                        .map(ClusterUtil.EnrichedLayer::new)
                        .map(layer -> Map.entry(layer.centerValue(unsafeEnv), layer.valueAt(node.getValue())))
                        .allMatch(layerInfo -> ClusterUtil.hasCluster(node.getKey())
                                && Math.abs(layerInfo.getValue()) < layerInfo.getKey() - thr))
                .count();
        // Verify, for each node, how many are not classified but they need to be
        var nonClassified = unsafeEnv.getNodes().stream()
                .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                .filter(node -> layers.stream()
                        .map(ClusterUtil.EnrichedLayer::new)
                        .map(layer -> Map.entry(layer.centerValue(unsafeEnv), layer.valueAt(node.getValue())))
                        .allMatch(layerInfo -> !ClusterUtil.hasCluster(node.getKey())
                                && Math.abs(layerInfo.getValue()) > layerInfo.getKey() - thr))
                .count();
        return Map.of("errors", (wrongNodes + nonClassified) / (double) unsafeEnv.getNodes().size());
    }

    private static class EnrichedLayer<P extends Position2D<P>> {
        private BidimensionalGaussianLayer<P> layer;
        public EnrichedLayer(BidimensionalGaussianLayer<P> layer) {
            this.layer = layer;
        }
        public double valueAt(P position) {
            return layer.getValue(position);
        }
        public double centerValue(Environment<?, P> env) {
            return ClusterUtil.getCentralValue(layer, env);
        }
    }
}
