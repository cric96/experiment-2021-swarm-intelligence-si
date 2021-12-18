package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.loader.export.Extractor;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule;
import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Position2D;
import it.unibo.alchemist.model.interfaces.Reaction;
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
                        .map(unsafeEnv::getPosition)
                        .map(layer::getValue)
                        .mapToDouble(value -> (Double)value)
                        .filter(value -> value > thr)
                        .count()
                ).collect(Collectors.toList());

        return IntStream
                .range(0, columns.size())
                .mapToObj(i -> Map.entry(columns.get(i), countsNodeForLayers.get(i)))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().doubleValue()));
    }
}
