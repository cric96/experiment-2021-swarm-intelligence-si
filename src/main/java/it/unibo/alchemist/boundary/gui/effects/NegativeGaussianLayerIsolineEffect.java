package it.unibo.alchemist.boundary.gui.effects;

/**
 * Effect used to draw isoline of a Gaussian Layer with a negative mean.
 */
public class NegativeGaussianLayerIsolineEffect extends DrawBidimensionalGaussianLayersIsolines {
    @Override
    protected LayerToFunctionMapper createMapper() {
        return new NegativeGaussianMapper();
    }
}
