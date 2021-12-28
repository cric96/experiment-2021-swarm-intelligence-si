package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.loader.export.Extractor;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule;
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager;
import it.unibo.alchemist.model.interfaces.Time;
import it.unibo.alchemist.model.interfaces.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        final Environment<T, Position2D<?>> unsafeEnv = (Environment<T, Position2D<?>>) environment;
        // Extract all gaussian layers
        var layers = ClusterUtil.getGaussian(unsafeEnv, columns);
        // Verify, for each node, it they are misclassified => node has a cluster but no Gaussian layer is near to them.
        var wrongNodes = unsafeEnv.getNodes().stream()
                .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                .filter(node -> layers.stream()
                        .map(layer -> Map.entry(node.getKey(), (Double) layer.getValue(node.getValue())))
                        .allMatch(nodeAndValue -> ClusterUtil.hasCluster(nodeAndValue.getKey())
                                && Math.abs(nodeAndValue.getValue()) < thr))
                .count();
        // Verify, for each node, how many are not classified but they need to be
        var nonClassified = unsafeEnv.getNodes().stream()
                .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                .filter(node -> layers.stream()
                        .map(layer -> Map.entry(node.getKey(), (Double) layer.getValue(node.getValue())))
                        .allMatch(nodeAndValue -> !ClusterUtil.hasCluster(nodeAndValue.getKey())
                                && Math.abs(nodeAndValue.getValue()) > thr))
                .count();
        return Map.of("errors", (wrongNodes + nonClassified) / (double) unsafeEnv.getNodes().size());
    }
}
