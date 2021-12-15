package it.unibo.alchemist.boundary.gui.effects;

public class NegativeGaussianLayerIsolineEffect extends DrawBidimensionalGaussianLayersIsolines {
    @Override
    protected LayerToFunctionMapper createMapper() {
        return new NegativeGaussianMapper();
    }
}
