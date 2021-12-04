package it.unibo.alchemist.model.implementation.layers;

import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Layer;
import it.unibo.alchemist.model.interfaces.Position;

public class LayerAggregator<P extends Position<P>> implements Layer<Double, P> {
    private final Environment<Double, P> env;
    public LayerAggregator(Environment<Double, P> env) {
        this.env = env;
    }

    @Override
    public Double getValue(P p) {
        return env.getLayers().stream().filter(l -> l != this).mapToDouble(l -> l.getValue(p)).sum();
    }
}
