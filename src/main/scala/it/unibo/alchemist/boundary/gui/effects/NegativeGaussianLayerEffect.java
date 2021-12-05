package it.unibo.alchemist.boundary.gui.effects;

import it.unibo.alchemist.boundary.wormhole.interfaces.Wormhole2D;
import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer;
import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Layer;
import it.unibo.alchemist.model.interfaces.Position2D;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NegativeGaussianLayerEffect extends DrawLayersGradient{
    public NegativeGaussianLayerEffect() {
        super();
    }
    @Override
    protected LayerToFunctionMapper createMapper() {
        return new LayerToFunctionMapper() {
            Boolean maxMinToSet = true;
            @Override
            public <T, P extends Position2D<P>> void prepare(
                    @NotNull DrawLayersValues effect,
                    @NotNull Collection<? extends Layer<T, P>> toDraw,
                    @NotNull Environment<T, P> env, @NotNull Graphics2D g,
                    @NotNull Wormhole2D<P> wormhole
            ) {
                if(maxMinToSet) {
                    final double minValue = toDraw.stream()
                        .filter(l -> l instanceof BidimensionalGaussianLayer)
                        .map(l -> (BidimensionalGaussianLayer<P>)(l))
                        .mapToDouble(l -> l.getValue(env.makePosition(l.getCenterX(), l.getCenterY())))
                        .min()
                        .orElse(0);
                    System.out.println(minValue);
                    effect.setMinLayerValue("0");
                    effect.setMaxLayerValue("" + minValue * -1);
                    maxMinToSet = false;
                }
            }

            @NotNull
            @Override
            public <T, P extends Position2D<P>> Collection<Function<? super P, ? extends Number>> map(
                    @NotNull Collection<? extends Layer<T, P>> collection
            ) {
                return map(collection.stream().collect(Collectors.toUnmodifiableList()));
            }

            @NotNull
            @Override
            public <T, P extends Position2D<P>> Stream<Function<? super P, ? extends Number>> map(
                    @NotNull Stream<Layer<T, P>> stream
            ) {
                return stream
                    .filter(l -> l instanceof BidimensionalGaussianLayer)
                    .map(l -> (BidimensionalGaussianLayer<P>)(l))
                    .map(l -> (P p) -> l.getValue(p) * -1);
            }
        };
    }
}
