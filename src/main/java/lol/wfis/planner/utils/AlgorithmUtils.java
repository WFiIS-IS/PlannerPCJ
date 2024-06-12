package lol.wfis.planner.utils;

public class AlgorithmUtils {
    public static int adaptPopulationSizeToWorkerNumber(int populationSize, int id, int size) {
        int newPopulationSize = populationSize;

        if (populationSize % size != 0) {
            newPopulationSize = populationSize + size - (populationSize % size);

            if (id == 0) {
                System.out.println(
                        "Changing population size from " + populationSize + " to " + newPopulationSize
                                + ", to match node number");
            }
        }

        return newPopulationSize;
    }
}
