#include <iostream>
#include <vector>
#include <random>
#include <chrono>
#include <thread>

using namespace std;
using namespace chrono;

const double INF = 1e9;

vector<vector<double>> generateGraph(int n, double density = 0.9) {
    random_device rd;
    mt19937 gen(rd());
    uniform_real_distribution<> weightDist(1.0, 10000.0);
    uniform_real_distribution<> edgeDist(0.0, 1.0);

    vector<vector<double>> graph(n, vector<double>(n, INF));
    for (int i = 0; i < n; i++) {
        graph[i][i] = 0;
        for (int j = 0; j < n; j++) {
            if (i != j && edgeDist(gen) < density) {
                graph[i][j] = weightDist(gen);
            }
        }
    }
    return graph;
}


// ---------------- ФЛОЙД — ПОСЛІДОВНИЙ ----------------
void floydSequential(vector<vector<double>>& dist) {
    int n = dist.size();
    for (int k = 0; k < n; k++) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (dist[i][k] + dist[k][j] < dist[i][j]) {
                    dist[i][j] = dist[i][k] + dist[k][j];
                }
            }
        }
    }
}

// ---------------- ФЛОЙД — ПАРАЛЕЛЬНИЙ ----------------
void floydParallel(vector<vector<double>>& dist, int threadsCount) {
    int n = dist.size();
    for (int k = 0; k < n; k++) {
        vector<thread> threads;
        int rowsPerThread = (n + threadsCount - 1) / threadsCount;

        for (int t = 0; t < threadsCount; t++) {
            int start = t * rowsPerThread;
            int end = min(start + rowsPerThread, n);

            threads.emplace_back([&, start, end, k]() {
                for (int i = start; i < end; i++) {
                    for (int j = 0; j < n; j++) {
                        if (dist[i][k] + dist[k][j] < dist[i][j]) {
                            dist[i][j] = dist[i][k] + dist[k][j];
                        }
                    }
                }
            });
        }

        for (auto& th : threads) th.join();
    }
}


int main() {
    int n;
    cout << "Введіть кількість вершин графа: ";
    cin >> n;

    int a, b;
    cout << "Введіть номери вершин a і b (0.." << n - 1 << "): ";
    cin >> a >> b;

    int k;
    cout << "Введіть кількість потоків: ";
    cin >> k;

    auto graph = generateGraph(n);

    auto distSeq = graph;
    auto distPar = graph;

    auto start1 = high_resolution_clock::now();
    floydSequential(distSeq);
    auto end1 = high_resolution_clock::now();

    auto start2 = high_resolution_clock::now();
    floydParallel(distPar, k);
    auto end2 = high_resolution_clock::now();

    auto timeSeq = duration_cast<milliseconds>(end1 - start1).count();
    auto timePar = duration_cast<milliseconds>(end2 - start2).count();

    cout << "\nПослідовна версія: " << timeSeq << " мс\n";
    cout << "Паралельна версія (" << k << " потоків): " << timePar << " мс\n\n";

    cout << "Найкоротша відстань між вершинами " << a << " і " << b << ": " << distSeq[a][b] << "\n";


    double speedup = (double)timeSeq / timePar;
    double efficiency = speedup / k;

    cout << "Speedup: " << speedup << endl;
    cout << "Efficiency: " << efficiency << endl;

    return 0;

}

