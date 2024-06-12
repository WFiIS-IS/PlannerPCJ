package lol.wfis.planner.models;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

public class Individual extends ArrayList<Chromosome> {
    @Getter
    @Setter
    int adaptation = -1000;

    public Individual() {
        this(0);
    }

    public Individual(int numberOfChromosomes) {
        super(numberOfChromosomes);
    }
}