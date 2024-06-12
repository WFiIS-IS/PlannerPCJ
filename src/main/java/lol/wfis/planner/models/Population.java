package lol.wfis.planner.models;

import java.util.ArrayList;
import java.util.Collection;

public class Population extends ArrayList<Individual> {
    public Population(int size) {
        super(size);
    }

    public Population(Collection<? extends Individual> c) {
        super(c);
    }
}