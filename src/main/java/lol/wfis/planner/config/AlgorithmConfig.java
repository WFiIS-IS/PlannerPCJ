package lol.wfis.planner.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmConfig {
    // The maximum number of generations to run the algorithm for
    private int maxGenerations = 100;
    // How many individuals to have in the population
    private int populationSize = 1_000;
    // How many genes are in each chromosome. Chromosome length
    private int numberOfPeriods = 8;
    // The probability of mutation occurring
    private double mutationProbability = 0.05;
}