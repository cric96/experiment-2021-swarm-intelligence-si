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

    public static <P extends Position2D<P>> List<BidimensionalGaussianLayer<P>> getGaussian(
        final Environment<?, P> env,
        final List<String> columns
    ) {
        return columns
            .stream()
            .map(SimpleMolecule::new)
            .map(env::getLayer)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(layer -> layer instanceof BidimensionalGaussianLayer)
            .map(layer -> (BidimensionalGaussianLayer<P>) layer)
            .collect(Collectors.toList());
    }

    public static <P extends Position2D<P>> double getCentralValue(
            BidimensionalGaussianLayer<P> layer,
            Environment<?, P> env) {
        var centerX = layer.getCenterX();
        var centerY = layer.getCenterY();
        return layer.getValue(env.makePosition(centerX, centerY));
    }

    public static class EnrichedLayer<P extends Position2D<P>> {
        private BidimensionalGaussianLayer<P> layer;
        public EnrichedLayer(BidimensionalGaussianLayer<P> layer) {
            this.layer = layer;
        }
        public double valueAt(P position) {
            return layer.getValue(position);
        }
        public double centerValue(Environment<?, P> env) {
            return Math.abs(ClusterUtil.getCentralValue(layer, env));
        }
    }
}
