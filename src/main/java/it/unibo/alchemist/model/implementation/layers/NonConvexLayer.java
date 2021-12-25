package it.unibo.alchemist.model.implementation.layers;

import it.unibo.alchemist.model.interfaces.Layer;
import it.unibo.alchemist.model.interfaces.Position2D;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * A layer used to assign a value to a possibly non-convex zone delimited by a sequence of points
 * @param <P> the Position type of the environment
 */
public class NonConvexLayer<P extends Position2D<P>> implements Layer<Double, P> {
    private final Polygon polygon;
    private final Double baseline;
    public NonConvexLayer(final Double baseline, final Integer ... points) {
        var list = Arrays.asList(points);
        if(list.size() % 2 != 0) {
            throw new IllegalArgumentException("An odd elements number is passed (works only in a 2D space)");
        }
        var xCoords = indexOf(list).filter(i -> i % 2 == 0).mapToInt(list::get).toArray();
        var yCoords = indexOf(list).filter(i -> i % 2 != 0).mapToInt(list::get).toArray();

        this.polygon = new Polygon(xCoords, yCoords, xCoords.length);
        this.baseline = baseline;
    }
    @Override
    public Double getValue(P p) {
        if(polygon.contains(p.getX(), p.getY())) {
            return this.baseline;
        } else {
            return 0.0;
        }
    }

    private Stream<Integer> indexOf(Collection<?> collection) {
        return Stream.iterate(0, i -> i + 1).limit(collection.size());
    }
}