package lol.wfis.planner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.pcj.PCJ;
import org.pcj.StartPoint;
import org.pcj.Storage;

import com.opencsv.bean.CsvToBeanBuilder;

import lol.wfis.planner.config.AlgorithmConfig;
import lol.wfis.planner.models.Chromosome;
import lol.wfis.planner.models.Gene;
import lol.wfis.planner.models.Individual;
import lol.wfis.planner.models.Population;
import lol.wfis.planner.models.Tuple;
import lol.wfis.planner.utils.AlgorithmUtils;

import org.pcj.RegisterStorage;

class WeightedCollection<E> {

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

record Pair<U, V>(U first, V second) {
}

@RegisterStorage(Main.Shared.class)
public class Main implements StartPoint {

    public Pair<Individual, Individual> randParents(Population parents) {
        var sortedParents = parents.stream()
                .sorted((a, b) -> Integer.compare(b.getAdaptation(), a.getAdaptation()))
                .collect(Collectors.toList());

        var weightedCollection = new WeightedCollection<Individual>();

        for (int i = 0; i < sortedParents.size(); i++) {
            var weight = Math.exp((-0.3 * i) + 2);

            weightedCollection.add(weight, sortedParents.get(i));
        }

        var mother = weightedCollection.next();
        var father = weightedCollection.next();

        while (mother == father) {
            father = weightedCollection.next();
        }

        return new Pair<Individual, Individual>(mother, father);
    }

    public Individual crossover(AlgorithmConfig config, Population population) {
        var parents = randParents(population);

        var mother = parents.first();
        var father = parents.second();

        var child = new Individual(config.getNumberOfPeriods());

        var rng = new Random();

        for (int i = 0; i < config.getNumberOfPeriods(); i++) {
            var motherChromosome = mother.get(i);
            var fatherChromosome = father.get(i);

            if (motherChromosome.getId() != fatherChromosome.getId()) {
                throw new RuntimeException("Mother and father chromosomes should have the same id");
            }

            var matingPointUpperBound = Math.min(motherChromosome.size(), fatherChromosome.size());

            var matingPoint = rng.nextInt(matingPointUpperBound + 1);

            var motherLeft = motherChromosome.subList(0, matingPoint);
            var fatherRight = fatherChromosome.subList(matingPoint, fatherChromosome.size());

            var childChromosome = new Chromosome(i);

            childChromosome.addAll(motherLeft);
            childChromosome.addAll(fatherRight);

            child.add(childChromosome);
        }

        // repair lost
        var allGenes = mother.stream().flatMap(c -> c.stream()).collect(Collectors.toList());

        var lostGenes = allGenes.stream().filter(g -> !child.stream().anyMatch(c -> c.contains(g)))
                .collect(Collectors.toList());

        for (var gene : lostGenes) {
            var periodId = rng.nextInt(config.getNumberOfPeriods());
            child.get(periodId).add(gene);
        }

        // remove duplicates
        var seen = new HashSet<Gene>();

        for (var period : child) {
            period.removeIf(x -> !seen.add(x));
        }

        {

            int reguiredGeneCount = 36;

            if (child.stream().mapToInt(c -> c.size()).sum() != reguiredGeneCount) {
                throw new RuntimeException("Child chromosome should have " + reguiredGeneCount + " genes, but has "
                        + child.stream().mapToInt(c -> c.size()).sum() + " genes");
            }
        }

        return child;
    }

    public void mutate(AlgorithmConfig config, Individual individual) {
        var mutationProbability = config.getMutationProbability();
        var numberOfPeriods = config.getNumberOfPeriods();

        var rng = new Random();

        for (int periodId = 0; periodId < numberOfPeriods; periodId++) {
            if (rng.nextDouble() < mutationProbability) {
                var geneCount = individual.get(periodId).size();

                if (geneCount == 0) {
                    continue;
                }

                var geneIndex = rng.nextInt(geneCount);

                var gene = individual.get(periodId).remove(geneIndex);

                // add gene to random period
                var targetPeriod = rng.nextInt(numberOfPeriods);

                while (targetPeriod == periodId) {
                    targetPeriod = rng.nextInt(numberOfPeriods);
                }

                individual.get(targetPeriod).add(gene);
            }
        }
    }

    public int calculateFitness(Individual individual, List<Tuple> tuples, boolean debug) {
        var individualFitness = 0;

        for (var chromosome : individual) {

            for (var gene : chromosome) {
                // if teacher is teaching more than one class at the same time decrease fitness
                // by 10

                var tuple = tuples.stream().filter(t -> t.getId() == gene.getId()).findFirst()
                        .orElseThrow(() -> new RuntimeException("Tuple with id " + gene.getId() + " not found"));

                var otherClasses = tuples.stream()
                        .filter(t -> chromosome.stream()
                                .map(Gene::getId)
                                .filter(geneId -> geneId.equals(t.getId()))
                                .findFirst()
                                .isPresent())
                        .filter(t -> t.getId() != tuple.getId())
                        .collect(Collectors.toList());

                var sameTeacherDifferentClassesCount = otherClasses.stream()
                        .filter(t -> t.getRoom().equals(tuple.getRoom()) && t.getTeacher().equals(tuple.getTeacher()))
                        .count();

                individualFitness -= sameTeacherDifferentClassesCount * 10;

                var sameRoomDifferentTeacherCount = otherClasses.stream()
                        .filter(t -> t.getRoom().equals(tuple.getRoom()) && !t.getTeacher().equals(tuple.getTeacher()))
                        .count();

                individualFitness -= sameRoomDifferentTeacherCount * 20;

                var sameTeacherSameSubjectCount = otherClasses.stream()
                        .filter(t -> t.getTeacher().equals(tuple.getTeacher()) && t.getLabel().equals(tuple.getLabel()))
                        .count();

                individualFitness -= sameTeacherSameSubjectCount * 10;

                var sameTeacherDifferentSubjectCount = otherClasses.stream()
                        .filter(t -> t.getTeacher().equals(tuple.getTeacher())
                                && !t.getLabel().equals(tuple.getLabel()))
                        .count();

                individualFitness -= sameTeacherDifferentSubjectCount * 20;

                if (debug) {
                    System.out.println("sameTeacherDifferentClassesCount: " + sameTeacherDifferentClassesCount
                            + ", sameRoomDifferentTeacherCount: " + sameRoomDifferentTeacherCount
                            + ", sameTeacherSameSubjectCount: " + sameTeacherSameSubjectCount
                            + ", sameTeacherDifferentSubjectCount: " + sameTeacherDifferentSubjectCount);
                }
            }
        }

        return individualFitness;
    }

    public Population createFirstPopulation(AlgorithmConfig config, List<Tuple> tuples) {
        var population = new Population(config.getPopulationSize());

        var rng = new Random();

        for (int i = 0; i < config.getPopulationSize(); i++) {
            var individual = new Individual(config.getNumberOfPeriods());

            for (int periodId = 0; periodId < config.getNumberOfPeriods(); periodId++) {
                var period = new Chromosome(periodId);

                individual.add(period);
            }

            for (var tuple : tuples) {
                var randomPeriodIndex = rng.nextInt(config.getNumberOfPeriods());
                var gene = new Gene(tuple.getId());
                individual.get(randomPeriodIndex).add(gene);
            }

            population.add(individual);
        }

        return population;
    }

    @Storage(Main.class)
    enum Shared {
        c,
        test
    }

    long c;
    TestCustomObjectToBeShared test;

    @Override
    public void main() throws IOException {
        var id = PCJ.myId();
        var worldSize = PCJ.threadCount();

        if (id != 0) {
            return;
        }

        var conifg = new AlgorithmConfig();

        String tuplesFile = "tuples.csv";

        var tuples = new CsvToBeanBuilder<Tuple>(new FileReader(tuplesFile))
                .withType(Tuple.class)
                .build()
                .parse();

        var newPopulationSize = AlgorithmUtils.adaptPopulationSizeToWorkerNumber(conifg.getPopulationSize(), id,
                worldSize);
        conifg.setPopulationSize(newPopulationSize);

        System.out.println("Algorithm configuration: " + conifg);

        var population = createFirstPopulation(conifg, tuples);

        for (int generation = 0; generation < conifg.getMaxGenerations(); generation++) {

            Population populationToBeProcessed = (Population) SerializationUtils.clone(population); // temp, while we
                                                                                                    // don't have
                                                                                                    // gather_and_synchronize
            if (id == 0) {
                System.out.println("Generation: " + generation);
            }

            var p = population;

            var newPopulation = populationToBeProcessed.stream()
                    .<Individual>map(individual -> crossover(conifg, p))
                    .map(individual -> {
                        mutate(conifg, individual);
                        return individual;
                    })
                    .map(individual -> {
                        individual.setAdaptation(calculateFitness(individual, tuples, false));
                        return individual;
                    })
                    .collect(Collectors.toList());

            populationToBeProcessed = new Population(newPopulation);

            population = populationToBeProcessed; // temp, while we don't have gather_and_synchronize

            // population.sort_by(|a, b| b.adaptation.partial_cmp(&a.adaptation).unwrap());

            population.sort((a, b) -> Integer.compare(b.getAdaptation(), a.getAdaptation()));

            if (id == 0) {
                System.out.println("Best adaptation: " + population.get(0).getAdaptation());
            }

            if (population.get(0).getAdaptation() == 0) {

                var bestIndividual = population.get(0);

                // create timetable.txt

                File timetableFile = new File("timetable.txt");
                var writer = new java.io.FileWriter(timetableFile);

                for (int i = 0; i < bestIndividual.size(); i++) {
                    var chromosome = bestIndividual.get(i);

                    var mappedTuples = chromosome.stream()
                            .map(gene -> tuples.stream().filter(t -> t.getId() == gene.getId()).findFirst().get());

                    var tuplesAsString = mappedTuples.map(t -> t.toString()).collect(Collectors.joining("\n - "));

                    // write to timetable.txt

                    writer.write(i + 1 + ":\n - " + tuplesAsString + "\n");
                }

                writer.close();

                break;
            }
        }

    }

    public static void main(String[] args) throws Exception {
        String nodesFile = "nodes.txt";
        PCJ.executionBuilder(Main.class)
                .addNodes(new File(nodesFile))
                .start();
    }
}

////////////////////////////////////////////

// System.out.println("Hello World from PCJ Thread " + PCJ.myId()
// + " out of " + PCJ.threadCount());

// // test = new TestCustomObjectToBeShared();
// // test.list = new java.util.ArrayList<>();

// PCJ.barrier();

// if (PCJ.myId() == 0) {
// System.out.println("Hello World from PCJ Thread " + PCJ.myId()
// + " out of " + PCJ.threadCount());

// test = new TestCustomObjectToBeShared();
// test.list = new java.util.ArrayList<>();
// test.list.add(1.1);
// test.list.add(2.2);
// test.list.add(3.3);

// System.out.println("Test: " + test.list);
// }

// PCJ.barrier();

// if (PCJ.myId() == 1) {
// System.out.println("Hello World from PCJ Thread " + PCJ.myId()
// + " out of " + PCJ.threadCount());

// var a = PCJ.<TestCustomObjectToBeShared>get(0, Shared.test);

// System.out.println("Test: " + a.list);
// }

// // default example program below
// PCJ.barrier();

// Random r = new Random();

// long nAll = 1000000;
// long n = nAll / PCJ.threadCount();
// double Rsq = 1.0;
// long circleCount;
// // Calculate
// circleCount = 0;
// double time = System.nanoTime();

// for (long i = 0; i < n; i++) {
// double x = 2.0 * r.nextDouble() - 1.0;
// double y = 2.0 * r.nextDouble() - 1.0;
// if ((x * x + y * y) < Rsq) {
// circleCount++;
// }
// }

// c = circleCount;
// PCJ.barrier();
// // Communicate results
// var cL = new PcjFuture[PCJ.threadCount()];

// long c0 = c;
// if (PCJ.myId() == 0) {
// for (int p = 1; p < PCJ.threadCount(); p++) {
// cL[p] = PCJ.asyncGet(p, Shared.c);
// }
// for (int p = 1; p < PCJ.threadCount(); p++) {
// c0 = c0 + (long) cL[p].get();
// }
// }

// PCJ.barrier();

// double pi = 4.0 * (double) c0 / (double) nAll;
// time = System.nanoTime() - time;
// // Print results
// if (PCJ.myId() == 0) {
// System.out.println(pi + " " + time * 1.0E-9);
// }