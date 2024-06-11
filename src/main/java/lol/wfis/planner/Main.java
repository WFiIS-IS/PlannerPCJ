package lol.wfis.planner;

import org.pcj.*;

import java.io.File;
import java.io.IOException;

public class Main implements StartPoint {

    public static void main(String[] args) throws IOException {
        String nodesFile = "nodes.txt";
        PCJ.executionBuilder(Main.class)
                .addNodes(new File(nodesFile))
                .start();
    }

    @Override
    public void main() throws Throwable {
        System.out.println("Hello World from PCJ Thread " + PCJ.myId()
                + " out of " + PCJ.threadCount());
    }
}
