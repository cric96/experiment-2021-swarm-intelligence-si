package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.loader.export.Extractor;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule;
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager;
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition;
import it.unibo.alchemist.model.interfaces.*;
import it.unibo.alchemist.model.interfaces.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An extractor used to count the number of node that belongs to some cluster.
 * This extractor works only for Gaussian layers
 */
public class ClusterEvaluation implements Extractor<Double> {
    private final Double thr;
    private final List<String> columns;

    /**
     * @param thr used to define if a node is inside or outside a cluster (i.e. Gaussian Layer)
     * @param names the names of clusters exported in the CSV file
     */
    public ClusterEvaluation(Double thr, String... names) {
        this.columns = Arrays.asList(names);
        this.thr = thr;
    }
    @NotNull
    @Override
    public List<String> getColumnNames() {
        return this.columns;
    }

    @NotNull
    @Override
    public <T> Map<String, Double> extractData(@NotNull Environment<T, ?> environment, @Nullable Reaction<T> reaction, @NotNull Time time, long l) {
        final var unsafeEnv = (Environment<T, Euclidean2DPosition>) environment;
        // Find all gaussian layers
        var layers = ClusterUtil.getGaussian(unsafeEnv, columns);

        // For each layers, count how many node belongs to it
        var countsNodeForLayers = layers.stream().map(ClusterUtil.EnrichedLayer::new).map(layer ->
                unsafeEnv.getNodes()
                        .stream()
                        .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                        .map(node -> Map.entry(node.getKey(), layer.valueAt(node.getValue())))
                        .filter(node -> Math.abs(node.getValue()) > layer.centerValue(unsafeEnv) - thr
                                && ClusterUtil.hasCluster(node.getKey()))
                        .mapToDouble(Map.Entry::getValue)
                        .count()
                ).collect(Collectors.toList());
        // Map each name to the node layer count
        return IntStream
                .range(0, columns.size())
                .mapToObj(i -> {
                    if(i >= countsNodeForLayers.size()) {
                        return Map.entry(columns.get(i), 0);
                    } else {
                        return Map.entry(columns.get(i), countsNodeForLayers.get(i));
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().doubleValue()));
    }
}
