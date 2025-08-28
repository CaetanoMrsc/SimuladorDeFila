import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class Main {

    // parametros
    static final double ARR_MIN = 2.0;   // chegada U[2,5]
    static final double ARR_MAX = 5.0;
    static final double SER_MIN = 3.0;   // servico U[3,5]
    static final double SER_MAX = 5.0;

    static final int CAPACITY = 5;       
    static final int N_ARRIVALS = 100_000; 
    static final long SEED = 42L; 

    enum Type { ARRIVAL, DEPARTURE }

    static class Event implements Comparable<Event> {
        final double time;
        final Type type;
        final int serverId;

        Event(double time, Type type) {
            this(time, type, -1);
        }
        Event(double time, Type type, int serverId) {
            this.time = time;
            this.type = type;
            this.serverId = serverId;
        }
        public int compareTo(Event o) {
            return Double.compare(this.time, o.time);
        }
    }

    static class SimResult {
        final String label;
        final double[] timeInState; 
        final double totalTime;
        final long lost;
        final int K;

        SimResult(String label, double[] timeInState, double totalTime, long lost, int K) {
            this.label = label;
            this.timeInState = timeInState;
            this.totalTime = totalTime;
            this.lost = lost;
            this.K = K;
        }

        void print() {
            System.out.println("=== " + label + " ===");
            System.out.printf(Locale.US, "Tempo global da simulação: %.6f\n", totalTime);
            System.out.println("Tempos acumulados por estado (n clientes no sistema):");
            for (int n = 0; n <= K; n++) {
                System.out.printf(Locale.US, "  n=%d: %.6f\n", n, timeInState[n]);
            }
            System.out.println("Distribuição de probabilidades (tempo em estado / tempo total):");
            for (int n = 0; n <= K; n++) {
                double p = timeInState[n] / totalTime;
                System.out.printf(Locale.US, "  P{n=%d} = %.6f\n", n, p);
            }
            System.out.println("Número de clientes perdidos: " + lost);
            System.out.println();
        }
    }

    static class GGcKSimulator {
        final int servers;  
        final int capacity; 
        final Random rng;

        // estado
        double clock = 0.0;
        int inSystem = 0; 
        int busy = 0;     

        // metricas de tempo por estado (n = 0..K)
        final double[] timeInState;

        final PriorityQueue<Event> fel = new PriorityQueue<>();

        final ArrayDeque<Integer> queue = new ArrayDeque<>(); 

        int arrivalsGenerated = 0;
        final int arrivalsTarget;

        long lost = 0;

        GGcKSimulator(int servers, int capacity, int arrivalsTarget, long seed) {
            this.servers = servers;
            this.capacity = capacity;
            this.arrivalsTarget = arrivalsTarget;
            this.timeInState = new double[capacity + 1];
            this.rng = new Random(seed);
        }

        private double u(double a, double b) {
            return a + rng.nextDouble() * (b - a);
        }

        SimResult run(String label) {
            scheduleArrival(2.0);
            double lastEventTime = 0.0;

            while (!fel.isEmpty()) {
                Event e = fel.poll();

                double dt = e.time - lastEventTime;
                if (dt < 0) dt = 0; 
                timeInState[inSystem] += dt;
                clock = e.time;
                lastEventTime = clock;

                switch (e.type) {
                    case ARRIVAL -> handleArrival();
                    case DEPARTURE -> handleDeparture(e.serverId);
                }

                if (arrivalsGenerated >= arrivalsTarget && queue.isEmpty() && busy == 0) break;
            }
            return new SimResult(label, timeInState, clock, lost, capacity);
        }

        private void scheduleArrival(double atTime) {
            fel.add(new Event(atTime, Type.ARRIVAL));
        }

        private void scheduleDeparture(double atTime, int serverId) {
            fel.add(new Event(atTime, Type.DEPARTURE, serverId));
        }

        private void handleArrival() {
            arrivalsGenerated++;
            if (arrivalsGenerated < arrivalsTarget) {
                double next = clock + u(ARR_MIN, ARR_MAX);
                scheduleArrival(next);
            }
            if (inSystem >= capacity) {
                lost++;
                return;
            }
            inSystem++;
            if (busy < servers) {
                int serverId = firstFreeServerId();
                busy++;
                double service = u(SER_MIN, SER_MAX);
                scheduleDeparture(clock + service, serverId);
            } else {
                queue.addLast(1);
            }
        }

        private void handleDeparture(int serverId) {
            // cliente sai do sistema
            inSystem--;
            // proximo passa para serviço
            if (!queue.isEmpty()) {
                queue.removeFirst();
                double service = u(SER_MIN, SER_MAX);
                scheduleDeparture(clock + service, serverId);
            } else {
                busy--;
            }
        }
        private int firstFreeServerId() {
            return busy; 
        }
    }

    public static void main(String[] args) {
        // --- G/G/1/5 ---
        GGcKSimulator sim1 = new GGcKSimulator(1, CAPACITY, N_ARRIVALS, SEED);
        SimResult r1 = sim1.run("G/G/1/5  (chegadas U[2,5], serviço U[3,5], 100k chegadas)");

        // --- G/G/2/5 ---
        GGcKSimulator sim2 = new GGcKSimulator(2, CAPACITY, N_ARRIVALS, SEED);
        SimResult r2 = sim2.run("G/G/2/5  (chegadas U[2,5], serviço U[3,5], 100k chegadas)");
        r1.print();
        r2.print();
    }
}
