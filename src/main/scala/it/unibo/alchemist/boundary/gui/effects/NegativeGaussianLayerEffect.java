package it.unibo.alchemist.boundary.gui.effects;

public class NegativeGaussianLayerEffect extends DrawLayersGradient{
    public NegativeGaussianLayerEffect() {
        super();
    }
    @Override
    protected LayerToFunctionMapper createMapper() {
        return new NegativeGaussianMapper();
    }
}
