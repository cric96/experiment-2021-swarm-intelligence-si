package it.unibo.alchemist.boundary.gui.effects;

import it.unibo.alchemist.boundary.swingui.effect.api.LayerToFunctionMapper;
import it.unibo.alchemist.boundary.swingui.effect.impl.DrawLayersGradient;

/**
 * Effect used to draw Gaussian Layer that has a negative value (it seems to be an Alchemist Bug, solve it).
 */
public class NegativeGaussianLayerEffect extends DrawLayersGradient {
    public NegativeGaussianLayerEffect() {
        super(new NegativeGaussianMapper());
    }
}
