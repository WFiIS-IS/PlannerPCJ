package lol.wfis.planner.utils;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class WeightedCollection<E> {

    private NavigableMap<Double, E> map = new TreeMap<>();
    private Random random;
    private double total = 0;

    public WeightedCollection() {
        this(new Random());
    }

    public WeightedCollection(Random random) {
        this.random = random;
    }

    public void add(Double weight, E object) {
        if (weight <= 0)
            return;
        total += weight;
        map.put(total, object);
    }

    public E next() {
        var value = random.nextDouble(total);
        return map.ceilingEntry(value).getValue();
    }
}
