import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Fork {
    private final Lock lock = new ReentrantLock();

    public boolean pickUp() {
        return lock.tryLock();
    }

    public void putDown() {
        lock.unlock();
    }
}

class Table {
    private final List<Fork> forks = new ArrayList<>();
    private final List<Philosopher> philosophers = new ArrayList<>();
    private boolean isDeadlocked = false;

    public Table(int size) {
        for (int i = 0; i < size; i++) {
            forks.add(new Fork());
        }
    }

    public void addPhilosopher(Philosopher philosopher) {
        philosophers.add(philosopher);
    }

    public Fork getLeftFork(int index) {
        return forks.get(index);
    }

    public Fork getRightFork(int index) {
        return forks.get((index + 1) % forks.size());
    }

    public void setDeadlocked(boolean deadlocked) {
        isDeadlocked = deadlocked;
    }

    public boolean isDeadlocked() {
        return isDeadlocked;
    }

    public boolean isFull() {
        return philosophers.size() == forks.size();
    }
}

class Philosopher implements Runnable {
    private static final Random random = new Random();
    private final String name;
    private Table table;
    private int seatIndex;
    private final SimulationClock clock;

    public Philosopher(String name, SimulationClock clock) {
        this.name = name;
        this.clock = clock;
    }

    public void setTable(Table table, int seatIndex) {
        this.table = table;
        this.seatIndex = seatIndex;
    }

    @Override
    public void run() {
        try {
            while (!table.isDeadlocked()) {
                think();
                if (eat()) {
                    putDownForks();
                } else {
                    // Deadlock detected, move to another table
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void think() throws InterruptedException {
        clock.sleep(random.nextInt(11));
    }

    private boolean eat() throws InterruptedException {
        Fork leftFork = table.getLeftFork(seatIndex);
        if (!leftFork.pickUp()) return false;

        clock.sleep(4);

        Fork rightFork = table.getRightFork(seatIndex);
        if (!rightFork.pickUp()) {
            leftFork.putDown();
            return false;
        }

        clock.sleep(random.nextInt(6));
        return true;
    }

    private void putDownForks() {
        table.getLeftFork(seatIndex).putDown();
        table.getRightFork(seatIndex).putDown();
    }

    public String getName() {
        return name;
    }
}

class SimulationClock {
    private final boolean useRealTime;
    private long simulatedTime = 0;

    public SimulationClock(boolean useRealTime) {
        this.useRealTime = useRealTime;
    }

    public void sleep(long seconds) throws InterruptedException {
        if (useRealTime) {
            Thread.sleep(seconds * 1000);
        } else {
            simulatedTime += seconds;
        }
    }

    public long getTime() {
        return useRealTime ? System.currentTimeMillis() / 1000 : simulatedTime;
    }
}

public class DiningPhilosophersSimulation {
    private static final int TABLE_SIZE = 5;
    private static final int NUM_TABLES = 6;

    public static void main(String[] args) {
        SimulationClock clock = new SimulationClock(false);
        List<Table> tables = new ArrayList<>();
        List<Philosopher> philosophers = new ArrayList<>();

        for (int i = 0; i < NUM_TABLES; i++) {
            tables.add(new Table(TABLE_SIZE));
        }

        for (int i = 0; i < NUM_TABLES - 1; i++) {
            for (int j = 0; j < TABLE_SIZE; j++) {
                Philosopher philosopher = new Philosopher("P" + i + j, clock);
                philosopher.setTable(tables.get(i), j);
                tables.get(i).addPhilosopher(philosopher);
                philosophers.add(philosopher);
            }
        }

        long startTime = clock.getTime();
        List<Thread> threads = new ArrayList<>();

        for (Philosopher philosopher : philosophers) {
            Thread t = new Thread(philosopher);
            threads.add(t);
            t.start();
        }

        Philosopher lastMovedPhilosopher = null;
        Table sixthTable = tables.get(NUM_TABLES - 1);

        while (!sixthTable.isDeadlocked()) {
            for (Table table : tables.subList(0, NUM_TABLES - 1)) {
                if (table.isDeadlocked()) {
                    Philosopher movingPhilosopher = table.philosophers.remove(0);
                    if (!sixthTable.isFull()) {
                        movingPhilosopher.setTable(sixthTable, sixthTable.philosophers.size());
                        sixthTable.addPhilosopher(movingPhilosopher);
                        lastMovedPhilosopher = movingPhilosopher;
                        Thread newThread = new Thread(movingPhilosopher);
                        threads.add(newThread);
                        newThread.start();
                    }
                    table.setDeadlocked(false);
                }
            }

            if (sixthTable.isFull()) {
                sixthTable.setDeadlocked(true);
            }

            try {
                clock.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long endTime = clock.getTime();

        for (Thread t : threads) {
            t.interrupt();
        }

        System.out.println("Simulation completed in " + (endTime - startTime) + " seconds.");
        System.out.println("Last philosopher who moved to the sixth table: " + 
                           (lastMovedPhilosopher != null ? lastMovedPhilosopher.getName() : "None"));
    }
}