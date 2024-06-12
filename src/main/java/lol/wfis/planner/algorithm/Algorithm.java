package lol.wfis.planner.algorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import lol.wfis.planner.config.AlgorithmConfig;
import lol.wfis.planner.models.Chromosome;
import lol.wfis.planner.models.Gene;
import lol.wfis.planner.models.Individual;
import lol.wfis.planner.models.Population;
import lol.wfis.planner.models.Tuple;
import lol.wfis.planner.utils.Pair;
import lol.wfis.planner.utils.WeightedCollection;

public class Algorithm {

    private static Pair<Individual, Individual> randParents(Population parents) {
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

    public static Individual crossover(AlgorithmConfig config, Population population) {
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

            childChromosome.addAll(motherLeft.stream().map(g -> new Gene(g.getId())).collect(Collectors.toList()));
            childChromosome.addAll(fatherRight.stream().map(g -> new Gene(g.getId())).collect(Collectors.toList()));

            child.add(childChromosome);
        }

        // repair lost
        var allGenes = mother.stream().flatMap(c -> c.stream()).collect(Collectors.toList());

        var lostGenes = allGenes.stream().filter(g -> !child.stream().anyMatch(c -> c.stream().map(Gene::getId).anyMatch(i -> i == g.getId())))
                .collect(Collectors.toList());

        for (var gene : lostGenes) {
            var periodId = rng.nextInt(config.getNumberOfPeriods());
            child.get(periodId).add(gene);
        }

        // remove duplicates
        var seen = new HashSet<Integer>();

        for (var period : child) {
            period.removeIf(x -> !seen.add(x.getId()));
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

    public static void mutate(AlgorithmConfig config, Individual individual) {
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

    public static int calculateFitness(Individual individual, List<Tuple> tuples, boolean debug) {
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

    public static Population createFirstPopulation(AlgorithmConfig config, List<Tuple> tuples) {
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
}
