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

public class ClusterMisclassifications implements Extractor<Long> {
    private final Double thr;
    private final List<String> columns;

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
    public <T> Map<String, Long> extractData(@NotNull Environment<T, ?> environment, @Nullable Reaction<T> reaction, @NotNull Time time, long l) {
        if(columns.isEmpty()) {
            return Map.of("errors", 0L);
        }
        final Environment<T, Position2D<?>> unsafeEnv = (Environment<T, Position2D<?>>) environment;
        var layers = columns
                .stream()
                .map(SimpleMolecule::new)
                .map(unsafeEnv::getLayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(layer -> layer instanceof BidimensionalGaussianLayer)
                .collect(Collectors.toList());

        var wrongNodes = unsafeEnv.getNodes().stream()
                .map(node -> Map.entry(node, unsafeEnv.getPosition(node)))
                .filter(node -> layers.stream()
                        .map(layer -> Map.entry(node.getKey(), (Double) layer.getValue(node.getValue())))
                        .allMatch(nodeAndValue -> hasCluster(nodeAndValue.getKey()) && Math.abs(nodeAndValue.getValue()) < thr))
                .count();

        return Map.of("errors", wrongNodes);
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
