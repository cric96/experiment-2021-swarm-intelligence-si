package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.loader.export.Extractor;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule;
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager;
import it.unibo.alchemist.model.interfaces.*;
import it.unibo.alchemist.model.interfaces.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClusterEvaluation implements Extractor<Double> {
    private final Double thr;
    private final List<String> columns;

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
        final Environment<T, Position2D<?>> unsafeEnv = (Environment<T, Position2D<?>>) environment;
        var layers = columns
                .stream()
                .map(SimpleMolecule::new)
                .map(unsafeEnv::getLayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(layer -> layer instanceof BidimensionalGaussianLayer)
                .collect(Collectors.toList());

        var countsNodeForLayers = layers.stream().map(layer ->
                unsafeEnv.getNodes()
                        .stream()
                        .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                        .map(node -> Map.entry(node.getKey(), (Double) layer.getValue(node.getValue())))
                        .filter(node -> Math.abs(node.getValue()) > thr && hasCluster(node.getKey()))
                        .mapToDouble(Map.Entry::getValue)
                        .count()
                ).collect(Collectors.toList());
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
    private Boolean hasCluster(Node<?> node) {
        var manager = new SimpleNodeManager<>(node);
        if(manager.has("clusters")) {
            var clusters = (scala.collection.Set<scala.Int>) manager.get("clusters");
            return clusters.nonEmpty();
        } {
            return false;
        }
    }
}
