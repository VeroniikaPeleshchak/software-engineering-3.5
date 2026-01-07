#include <iostream>
#include <vector>
#include <random>
#include <chrono>
#include <limits>
#include <omp.h>

const int INF = std::numeric_limits<int>::max();

struct Edge {
    int to;
    int weight;
};

void generateRandomGraph(int numNodes, double density, int maxWeight, std::vector<std::vector<Edge>>& adj) {
    adj.assign(numNodes, std::vector<Edge>());
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> weightDist(1, maxWeight);
    std::uniform_real_distribution<> probDist(0.0, 1.0);

    for (int i = 0; i < numNodes; ++i) {
        for (int j = i + 1; j < numNodes; ++j) {
            if (probDist(gen) < density) {
                int weight = weightDist(gen);
                adj[i].push_back({j, weight});
                adj[j].push_back({i, weight});
            }
        }
    }
}

// Послідовний алгоритм Дейкстри
void sequentialDijkstra(int startNode, int numNodes, const std::vector<std::vector<Edge>>& adj, std::vector<int>& dist) {
    std::vector<bool> visited(numNodes, false);
    dist.assign(numNodes, INF);
    dist[startNode] = 0;

    for (int i = 0; i < numNodes - 1; ++i) {
        int u = -1;
        int minDist = INF;

        for (int v = 0; v < numNodes; ++v) {
            if (!visited[v] && dist[v] < minDist) {
                minDist = dist[v];
                u = v;
            }
        }

        if (u == -1) break;

        visited[u] = true;

        for (const auto& edge : adj[u]) {
            if (!visited[edge.to] && dist[u] != INF && dist[u] + edge.weight < dist[edge.to]) {
                dist[edge.to] = dist[u] + edge.weight;
            }
        }
    }
}

// Паралельний алгоритм Дейкстри
void parallelDijkstra(int startNode, int numNodes, const std::vector<std::vector<Edge>>& adj, std::vector<int>& dist, int numThreads) {
    omp_set_num_threads(numThreads);
    std::vector<bool> visited(numNodes, false);
    dist.assign(numNodes, INF);
    dist[startNode] = 0;

    for (int i = 0; i < numNodes - 1; ++i) {
        int u = -1;
        int minDist = INF;

#pragma omp parallel
        {
            int localU = -1;
            int localMinDist = INF;

#pragma omp for nowait
            for (int v = 0; v < numNodes; ++v) {
                if (!visited[v] && dist[v] < localMinDist) {
                    localMinDist = dist[v];
                    localU = v;
                }
            }

#pragma omp critical
            {
                if (localMinDist < minDist) {
                    minDist = localMinDist;
                    u = localU;
                }
            }
        }

        if (u == -1) break;

        visited[u] = true;

#pragma omp parallel for
        for (const auto& edge : adj[u]) {
            if (!visited[edge.to] && dist[u] != INF && dist[u] + edge.weight < dist[edge.to]) {
                dist[edge.to] = dist[u] + edge.weight;
            }
        }
    }
}

void runExperiments(int numNodes, double density, int maxWeight, int startNode) {
    std::vector<std::vector<Edge>> adj;
    generateRandomGraph(numNodes, density, maxWeight, adj);

    std::cout << "\n--- Граф з " << numNodes << " вузлами та щільністю " << density << " ---\n";

    std::vector<int> dist_seq;
    auto start_seq = std::chrono::high_resolution_clock::now();
    sequentialDijkstra(startNode, numNodes, adj, dist_seq);
    auto end_seq = std::chrono::high_resolution_clock::now();
    double T1 = std::chrono::duration<double>(end_seq - start_seq).count();
    std::cout << "Послідовний Дейкстра: " << T1 << " сек\n";

    for (int k = 2; k <= 8; k *= 2) {
        std::vector<int> dist_par;
        auto start_par = std::chrono::high_resolution_clock::now();
        parallelDijkstra(startNode, numNodes, adj, dist_par, k);
        auto end_par = std::chrono::high_resolution_clock::now();
        double Tk = std::chrono::duration<double>(end_par - start_par).count();

        double speedup = T1 / Tk;
        double efficiency = speedup / k;

        std::cout << "Паралельний Дейкстра (" << k << " потоки): " << Tk << " сек | ";
        std::cout << "Speedup = " << speedup << " | Efficiency = " << efficiency << "\n";
    }
}

int main() {
    int startNode = 0;
    int maxWeight = 100;

    runExperiments(5000, 0.5, maxWeight, startNode);
    runExperiments(10000, 0.5, maxWeight, startNode);
    runExperiments(20000, 0.5, maxWeight, startNode);

    runExperiments(5000, 0.1, maxWeight, startNode);
    runExperiments(10000, 0.1, maxWeight, startNode);
    runExperiments(20000, 0.1, maxWeight, startNode);

    runExperiments(5000, 0.9, maxWeight, startNode);
    runExperiments(10000, 0.9, maxWeight, startNode);
    runExperiments(20000, 0.9, maxWeight, startNode);

    return 0;
}






