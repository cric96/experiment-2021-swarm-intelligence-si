package it.unibo.alchemist.loader.export.extractors;

import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule;
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager;
import it.unibo.alchemist.model.interfaces.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ClusterUtil {
    public static Boolean hasCluster(final Node<?> node) {
        var manager = new SimpleNodeManager<>(node);
        if(manager.has("clusters")) {
            var clusters = (scala.collection.Set<scala.Int>) manager.get("clusters");
            return clusters.nonEmpty();
        } {
            return false;
        }
    }

    public static List<Layer<?, Position2D<?>>> getGaussian(
        final Environment<?, Position2D<?>> env,
        final List<String> columns
    ) {
        return columns
            .stream()
            .map(SimpleMolecule::new)
            .map(env::getLayer)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(layer -> layer instanceof BidimensionalGaussianLayer)
            .collect(Collectors.toList());
    }
}
