package lol.wfis.planner;

import java.io.File;
import java.util.Random;

import org.pcj.PCJ;
import org.pcj.StartPoint;
import org.pcj.Storage;

import org.pcj.PcjFuture;
import org.pcj.RegisterStorage;

@RegisterStorage(Main.Shared.class)
public class Main implements StartPoint {

    @Storage(Main.class)
    enum Shared {
        c,
        test
    }

    long c;
    TestCustomObjectToBeShared test;

    @Override
    public void main() {
        System.out.println("Hello World from PCJ Thread " + PCJ.myId()
                + " out of " + PCJ.threadCount());

        // test = new TestCustomObjectToBeShared();
        // test.list = new java.util.ArrayList<>();

        PCJ.barrier();

        if (PCJ.myId() == 0) {
            System.out.println("Hello World from PCJ Thread " + PCJ.myId()
                    + " out of " + PCJ.threadCount());

            test = new TestCustomObjectToBeShared();
            test.list = new java.util.ArrayList<>();
            test.list.add(1.1);
            test.list.add(2.2);
            test.list.add(3.3);

            System.out.println("Test: " + test.list);
        }


        PCJ.barrier();

        if (PCJ.myId() == 1) {
            System.out.println("Hello World from PCJ Thread " + PCJ.myId()
                    + " out of " + PCJ.threadCount());

            var a = PCJ.<TestCustomObjectToBeShared>get(0, Shared.test);


            System.out.println("Test: " + a.list);
        }

        // default example program below
        PCJ.barrier();
        

        Random r = new Random();

        long nAll = 1000000;
        long n = nAll / PCJ.threadCount();
        double Rsq = 1.0;
        long circleCount;
        // Calculate
        circleCount = 0;
        double time = System.nanoTime();

        for (long i = 0; i < n; i++) {
            double x = 2.0 * r.nextDouble() - 1.0;
            double y = 2.0 * r.nextDouble() - 1.0;
            if ((x * x + y * y) < Rsq) {
                circleCount++;
            }
        }

        c = circleCount;
        PCJ.barrier();
        // Communicate results
        var cL = new PcjFuture[PCJ.threadCount()];

        long c0 = c;
        if (PCJ.myId() == 0) {
            for (int p = 1; p < PCJ.threadCount(); p++) {
                cL[p] = PCJ.asyncGet(p, Shared.c);
            }
            for (int p = 1; p < PCJ.threadCount(); p++) {
                c0 = c0 + (long) cL[p].get();
            }
        }

        PCJ.barrier();

        double pi = 4.0 * (double) c0 / (double) nAll;
        time = System.nanoTime() - time;
        // Print results
        if (PCJ.myId() == 0) {
            System.out.println(pi + " " + time * 1.0E-9);
        }
    }

    public static void main(String[] args) throws Exception {
        String nodesFile = "nodes.txt";
        PCJ.executionBuilder(Main.class)
                .addNodes(new File(nodesFile))
                .start();
    }
}