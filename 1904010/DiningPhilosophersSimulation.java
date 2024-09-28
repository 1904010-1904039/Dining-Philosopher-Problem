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
    private final List<Fork> forks;
    private final List<Philosopher> philosophers;
    private boolean isDeadlocked; // tracking Deadlock condition

    public Table(int size) { // table size 5
        forks = new ArrayList<>(size);
        philosophers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            forks.add(new Fork());
        }
        isDeadlocked = false;
    }

    public void addPhilosopher(Philosopher philosopher) {
        philosophers.add(philosopher);
    }

    public Fork getLeftFork(int index) {
        return forks.get(index);
    }

    public Fork getRightFork(int index) {
        return forks.get((index + 1) % forks.size()); // last ph's right fork is the left fork of first ph
    }

    public void setDeadlocked(boolean deadlocked) {
        isDeadlocked = deadlocked;
    }

    public boolean isDeadlocked() {
        return isDeadlocked;
    }

    public boolean isFull() { // 5 == 5
        return philosophers.size() == forks.size();
    }

    public Philosopher removePhilosopher(int index) {
        return philosophers.remove(index);
    }

    public int getPhilosopherCount() {
        return philosophers.size();
    }
}

class Philosopher extends Thread {
    private static final Random random = new Random();
    private final String philosopherName;
    private final SimulationClock clock;
    private boolean isEating;
    private long lastEatTime; // used to detect the deadlock

    // Tracking the table info
    private Table table;
    private int seatIndex;

    public Philosopher(String name, SimulationClock clock) {
        super(name);
        this.philosopherName = name;
        this.clock = clock;
        this.isEating = false;
        this.lastEatTime = clock.getTime();
    }

    // assigning the ph to a specific table
    public void setTable(Table table, int seatIndex) {
        this.table = table;
        this.seatIndex = seatIndex;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted() && !table.isDeadlocked()) {
                think();
                if (eat()) {
                    // after eating unlock both fork
                    putDownForks();
                }
            }
        } catch (InterruptedException e) {

        }
    }

    private void think() throws InterruptedException {
        isEating = false;
        clock.sleep(random.nextInt(10));
    }

    // returns true if ate
    private boolean eat() throws InterruptedException {
        Fork leftFork = table.getLeftFork(seatIndex);
        Fork rightFork = table.getRightFork(seatIndex);

        if (leftFork.pickUp()) { // first left fork lock
            if (rightFork.pickUp()) { // then right fork lock
                isEating = true;
                lastEatTime = clock.getTime();
                clock.sleep(random.nextInt(5)); // eating time
                return true;
            } else { // otherwise left fork unlock
                leftFork.putDown();
            }
        }
        return false;
    }

    private void putDownForks() {
        table.getLeftFork(seatIndex).putDown();
        table.getRightFork(seatIndex).putDown();
    }

    public String getPhilosopherName() {
        return philosopherName;
    }

    // Stuck condition
    public boolean isStuck(long currentTime) {
        return !isEating && (currentTime - lastEatTime) > 20;
    }
}

class SimulationClock {
    private long simulatedTime = 0;

    public void sleep(long seconds) {
        simulatedTime += seconds;
    }

    public long getTime() {
        return simulatedTime;
    }
}

public class DiningPhilosophersSimulation {
    private static final int TABLE_SIZE = 5;
    private static final int NUM_TABLES = 6;

    public static void main(String[] args) {
        SimulationClock clock = new SimulationClock();
        List<Table> tables = new ArrayList<>();
        List<Philosopher> allPhilosophers = new ArrayList<>();
        char philosopherName = 'A';

        for (int i = 0; i < NUM_TABLES; i++) {
            tables.add(new Table(TABLE_SIZE));
        }

        // iterating the first 5 tables
        for (int i = 0; i < NUM_TABLES - 1; i++) {
            for (int j = 0; j < TABLE_SIZE; j++) {
                Philosopher philosopher = new Philosopher(String.valueOf(philosopherName) + " from table " + i, clock);

                philosopher.setTable(tables.get(i), j); // assigining to the jth index of ith table
                // System.out.println("Assigning " + philosopherName + " to table[" + i + "][" + j + "]");
                tables.get(i).addPhilosopher(philosopher);
                allPhilosophers.add(philosopher);
                philosopherName++;
            }
        }

        long startTime = clock.getTime();
        Philosopher lastMovedPhilosopher = null;
        Table sixthTable = tables.get(NUM_TABLES - 1);

        // starting all the thread concurrently 
        for (Philosopher philosopher : allPhilosophers) {
            philosopher.start();
        }

        while (!sixthTable.isDeadlocked()) {
            for (int i = 0; i < NUM_TABLES - 1; i++) {
                Table table = tables.get(i);

                // checking if all the threads are in deadlock in the ith table
                if (table.getPhilosopherCount() > 0 && allPhilosophersStuck(table, clock.getTime())) {
                    System.out.println("All philosophers at table " + i + " are stuck");
                    Philosopher movingPhilosopher = table.removePhilosopher(0); // Moving the the first one 
                    movingPhilosopher.interrupt();

                    // If the sixth table has space, the interrupted thread is restarted at the sixth table.
                    if (!sixthTable.isFull()) {
                        movingPhilosopher = new Philosopher(movingPhilosopher.getPhilosopherName(), clock);
                        movingPhilosopher.setTable(sixthTable, sixthTable.getPhilosopherCount());
                        sixthTable.addPhilosopher(movingPhilosopher);
                        lastMovedPhilosopher = movingPhilosopher; // tracking the last thread that moved
                        movingPhilosopher.start();
                    }
                }
            }

            // no space in the 6th table and all the threads are in the deadlock
            if (sixthTable.isFull() && allPhilosophersStuck(sixthTable, clock.getTime())) {
                sixthTable.setDeadlocked(true);
            }

            clock.sleep(1);
        }

        long endTime = clock.getTime();

        // deadlock detected at table 6. Stop all the thread
        for (Philosopher philosopher : allPhilosophers) {
            philosopher.interrupt();
        }

        System.out.println("Simulation completed in " + (endTime - startTime) + " time units.");
        System.out.println("Last philosopher who moved to the sixth table: " + 
                           (lastMovedPhilosopher != null ? lastMovedPhilosopher.getPhilosopherName() : "None"));
    }

    // Checking all the theads are stuck at ith table
    private static boolean allPhilosophersStuck(Table table, long currentTime) {
        for (int i = 0; i < table.getPhilosopherCount(); i++) {
            Philosopher philosopher = (Philosopher) table.removePhilosopher(0);
            // checking if that particular thread isStucked
            boolean isStuck = philosopher.isStuck(currentTime);
            table.addPhilosopher(philosopher);
            if (!isStuck) {
                return false;
            }
        }
        return true;
    }
}