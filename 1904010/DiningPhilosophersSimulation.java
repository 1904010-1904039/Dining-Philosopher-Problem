import java.util.concurrent.*;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

class DiningPhilosophersSimulation {
    private static final int NUM_TABLES = 6;
    private static final int PHILOSOPHERS_PER_TABLE = 5;
    private static final int TOTAL_PHILOSOPHERS = 25;
    private static final long TIME_SCALE = 100; // Scale factor for time

    private static class Fork {
        private final Semaphore semaphore = new Semaphore(1);

        public void pickup() throws InterruptedException {
            semaphore.acquire();
        }

        public void putdown() {
            semaphore.release();
        }
    }

    private static class Table {
        private final Fork[] forks = new Fork[PHILOSOPHERS_PER_TABLE];
        private final Semaphore[] seats = new Semaphore[PHILOSOPHERS_PER_TABLE];
        private final CountDownLatch deadlockLatch = new CountDownLatch(PHILOSOPHERS_PER_TABLE);

        public Table() {
            for (int i = 0; i < PHILOSOPHERS_PER_TABLE; i++) {
                forks[i] = new Fork();
                seats[i] = new Semaphore(1);
            }
        }
    }

    private static class Philosopher implements Runnable {
        private final char label;
        private Table currentTable;
        private int seatIndex;
        private final Random random = new Random();

        public Philosopher(char label, Table initialTable, int initialSeatIndex) {
            this.label = label;
            this.currentTable = initialTable;
            this.seatIndex = initialSeatIndex;
        }

        private void think() throws InterruptedException {
            Thread.sleep(random.nextInt(11) * TIME_SCALE);
        }

        private void eat() throws InterruptedException {
            Thread.sleep(random.nextInt(6) * TIME_SCALE);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    think();

                    // Try to pick up left fork
                    currentTable.forks[seatIndex].pickup();

                    // Wait 4 seconds before trying to pick up right fork
                    Thread.sleep(4 * TIME_SCALE);

                    // Try to pick up right fork
                    int rightForkIndex = (seatIndex + 1) % PHILOSOPHERS_PER_TABLE;
                    try {
                        if (!currentTable.forks[rightForkIndex].semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                            // Deadlock detected
                            currentTable.forks[seatIndex].putdown();
                            currentTable.deadlockLatch.countDown();
                            moveToNewTable();
                            continue;
                        }
                    } catch (InterruptedException e) {
                        currentTable.forks[seatIndex].putdown();
                        continue;
                    }

                    // Eat
                    eat();

                    // Put down forks
                    currentTable.forks[seatIndex].putdown();
                    currentTable.forks[rightForkIndex].putdown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void moveToNewTable() throws InterruptedException {
            currentTable.seats[seatIndex].release();
            Table newTable = tables[NUM_TABLES - 1]; // Move to the last table
            int newSeatIndex = random.nextInt(PHILOSOPHERS_PER_TABLE);
            while (!newTable.seats[newSeatIndex].tryAcquire()) {
                newSeatIndex = random.nextInt(PHILOSOPHERS_PER_TABLE);
            }
            currentTable = newTable;
            seatIndex = newSeatIndex;
        }
    }

    private static final Table[] tables = new Table[NUM_TABLES];
    private static final List<Philosopher> philosophers = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        // Initialize tables
        for (int i = 0; i < NUM_TABLES; i++) {
            tables[i] = new Table();
        }

        // Create and start philosopher threads
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_PHILOSOPHERS);
        for (int i = 0; i < TOTAL_PHILOSOPHERS; i++) {
            char label = (char) ('A' + i);
            int tableIndex = i / PHILOSOPHERS_PER_TABLE;
            int seatIndex = i % PHILOSOPHERS_PER_TABLE;
            Philosopher philosopher = new Philosopher(label, tables[tableIndex], seatIndex);
            philosophers.add(philosopher);
            executor.execute(philosopher);
        }

        // Wait for the last table to deadlock
        tables[NUM_TABLES - 1].deadlockLatch.await();

        // Stop all threads
        executor.shutdownNow();

        // Find the last philosopher who moved to the sixth table
        char lastMovedPhilosopher = 'A';
        for (Philosopher philosopher : philosophers) {
            if (philosopher.currentTable == tables[NUM_TABLES - 1]) {
                lastMovedPhilosopher = philosopher.label;
            }
        }

        System.out.println("Simulation complete.");
        System.out.println("Last philosopher who moved to the sixth table: " + lastMovedPhilosopher);
    }
}