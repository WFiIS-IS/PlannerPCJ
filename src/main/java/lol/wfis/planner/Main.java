package lol.wfis.planner;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.SerializationUtils;
import org.pcj.PCJ;
import org.pcj.StartPoint;
import org.pcj.Storage;

import com.opencsv.bean.CsvToBeanBuilder;

import lol.wfis.planner.algorithm.Algorithm;
import lol.wfis.planner.config.AlgorithmConfig;
import lol.wfis.planner.models.Individual;
import lol.wfis.planner.models.Population;
import lol.wfis.planner.models.Tuple;
import lol.wfis.planner.utils.AlgorithmUtils;

import org.pcj.RegisterStorage;

@RegisterStorage(Main.Shared.class)
public class Main implements StartPoint {

    private static final String tuplesFile = "tuples.csv";
    private static final String timetableFile = "timetable.txt";
    private static final String nodesFile = "nodes.txt";

    AlgorithmConfig config;
    ArrayList<Tuple> tuples;
    Population population;
    ArrayList<Individual> newPopulationShard;

    @Storage(Main.class)
    enum Shared {
        config,
        tuples,
        population,
        newPopulationShard,
    }

    @Override
    public void main() throws IOException {
        var id = PCJ.myId();
        var worldSize = PCJ.threadCount();

        // Initialization: read config, tuples, create population
        if (id == 0) {
            config = new AlgorithmConfig();

            var newPopulationSize = AlgorithmUtils.adaptPopulationSizeToWorkerNumber(config.getPopulationSize(), id,
                    worldSize);
            config.setPopulationSize(newPopulationSize);

            System.out.println("Algorithm configuration: " + config);

            tuples = new CsvToBeanBuilder<Tuple>(new FileReader(tuplesFile))
                    .withType(Tuple.class)
                    .build()
                    .parse()
                    .stream()
                    .collect(Collectors.toCollection(ArrayList::new));

            population = Algorithm.createFirstPopulation(config, tuples);

            PCJ.broadcast(config, Shared.config);
            PCJ.broadcast(tuples, Shared.tuples);
            PCJ.broadcast(population, Shared.population);
        }

        PCJ.barrier();

        var individalRangeStart = id * config.getPopulationSize() / worldSize;
        var individualsPerNode = config.getPopulationSize() / worldSize;

        System.out.println("Range of individuals to be processed per node: [" + individalRangeStart + ", "
                + (individalRangeStart + individualsPerNode) + ")");

        // Processing
        for (int generation = 0; generation < config.getMaxGenerations(); generation++) {

            if (id == 0) {
                System.out.println("Generation: " + generation);
            }

            var populationToBeProcessed = population.stream()
                    .skip(individalRangeStart)
                    .limit(individualsPerNode);

            newPopulationShard = populationToBeProcessed
                    .map(_ -> Algorithm.crossover(config, population))
                    .map(individual -> {
                        Algorithm.mutate(config, individual);
                        return individual;
                    })
                    .map(individual -> {
                        individual.setAdaptation(Algorithm.calculateFitness(individual, tuples, false));
                        return individual;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            PCJ.barrier();

            if (id == 0) {

                population = new Population(newPopulationShard);

                for (int i = 1; i < worldSize; i++) {
                    var shard = PCJ.<ArrayList<Individual>>get(i, Shared.newPopulationShard);
                    population.addAll(shard);
                }

                population.sort((a, b) -> Integer.compare(b.getAdaptation(), a.getAdaptation()));
                PCJ.broadcast(population, Shared.population);
            }

            PCJ.barrier();

            var bestIndividual = population.get(0);

            if (id == 0) {
                System.out.println("Best adaptation: " + population.get(0).getAdaptation());

                if (bestIndividual.getAdaptation() == 0) {

                    File file = new File(timetableFile);
                    var writer = new java.io.FileWriter(file);

                    for (int i = 0; i < bestIndividual.size(); i++) {
                        var chromosome = bestIndividual.get(i);

                        var mappedTuples = chromosome.stream()
                                .map(gene -> tuples.stream().filter(t -> t.getId() == gene.getId()).findFirst().get());

                        var tuplesAsString = mappedTuples.map(t -> t.toString()).collect(Collectors.joining("\n - "));

                        writer.write(i + 1 + ":\n - " + tuplesAsString + "\n");
                    }

                    writer.close();

                    break;
                }
            }

            if (id != 0 && bestIndividual.getAdaptation() == 0) {
                break;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PCJ.executionBuilder(Main.class)
                .addNodes(new File(nodesFile))
                .start();
    }
}
