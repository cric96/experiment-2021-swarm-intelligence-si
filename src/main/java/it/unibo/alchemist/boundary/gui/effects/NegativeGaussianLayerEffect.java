package it.unibo.alchemist.boundary.gui.effects;

/**
 * Effect used to draw Gaussian Layer that has a negative value (it seems to be an Alchemist Bug, solve it).
 */
public class NegativeGaussianLayerEffect extends DrawLayersGradient {
    public NegativeGaussianLayerEffect() {
        super();
    }
    @Override
    protected LayerToFunctionMapper createMapper() {
        return new NegativeGaussianMapper();
    }
}
