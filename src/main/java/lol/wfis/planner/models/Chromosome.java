package lol.wfis.planner.models;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

public class Chromosome extends ArrayList<Gene> {
    @Getter
    @Setter
    private int id;

    public Chromosome(int id) {
        this(id, 0);
    }

    public Chromosome(int id, int size) {
        super(size);
        this.id = id;
    }
}