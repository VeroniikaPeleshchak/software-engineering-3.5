#include <iostream>
#include <vector>
#include <limits>
#include <chrono>
#include <random>
#include <thread>
#include <mutex>

using namespace std;

struct Edge {
    int from;
    int to;
    int weight;
};

vector<vector<int>> generateGraph(int V, int maxWeight) {
    vector<vector<int>> graph(V, vector<int>(V, 0));
    random_device rd;
    mt19937 gen(rd());
    uniform_int_distribution<> distrib(1, maxWeight);

    for (int i = 1; i < V; ++i) {
        int weight = distrib(gen);
        graph[i - 1][i] = weight;
        graph[i][i - 1] = weight;
    }

    for (int i = 0; i < V; ++i) {
        for (int j = i + 1; j < V; ++j) {
            if (graph[i][j] == 0 && distrib(gen) < maxWeight / 3) {
                int weight = distrib(gen);
                graph[i][j] = weight;
                graph[j][i] = weight;
            }
        }
    }
    return graph;
}

vector<Edge> sequentialPrim(const vector<vector<int>>& graph, int startNode) {
    int V = graph.size();
    vector<int> parent(V, -1);
    vector<int> key(V, numeric_limits<int>::max());
    vector<bool> inMST(V, false);
    vector<Edge> mst;

    key[startNode] = 0;

    for (int i = 0; i < V - 1; ++i) {
        int minKey = numeric_limits<int>::max();
        int u = -1;

        for (int v = 0; v < V; ++v) {
            if (!inMST[v] && key[v] < minKey) {
                minKey = key[v];
                u = v;
            }
        }

        if (u == -1) break;
        inMST[u] = true;

        if (parent[u] != -1) {
            mst.push_back({parent[u], u, graph[parent[u]][u]});
        }

        for (int v = 0; v < V; ++v) {
            if (graph[u][v] != 0 && !inMST[v] && graph[u][v] < key[v]) {
                parent[v] = u;
                key[v] = graph[u][v];
            }
        }
    }

    return mst;
}

vector<Edge> parallelPrim(const vector<vector<int>>& graph, int startNode, int k) {
    int V = graph.size();
    vector<int> parent(V, -1);
    vector<int> key(V, numeric_limits<int>::max());
    vector<bool> inMST(V, false);
    vector<Edge> mst;

    key[startNode] = 0;

    mutex minMutex;
    int minGlobalKey = numeric_limits<int>::max();
    int uGlobal = -1;

    for (int i = 0; i < V - 1; ++i) {
        minGlobalKey = numeric_limits<int>::max();
        uGlobal = -1;

        auto findMinWorker = [&](int start, int end) {
            int minLocalKey = numeric_limits<int>::max();
            int uLocal = -1;
            for (int v = start; v < end; ++v) {
                if (!inMST[v] && key[v] < minLocalKey) {
                    minLocalKey = key[v];
                    uLocal = v;
                }
            }
            lock_guard<mutex> lock(minMutex);
            if (minLocalKey < minGlobalKey) {
                minGlobalKey = minLocalKey;
                uGlobal = uLocal;
            }
        };

        vector<thread> threads;
        int verticesPerThread = V / k;
        for (int j = 0; j < k; ++j) {
            int start = j * verticesPerThread;
            int end = (j == k - 1) ? V : (j + 1) * verticesPerThread;
            threads.emplace_back(findMinWorker, start, end);
        }

        for (auto& t : threads) {
            t.join();
        }

        int u = uGlobal;
        if (u == -1) break;

        inMST[u] = true;
        if (parent[u] != -1) {
            mst.push_back({parent[u], u, graph[parent[u]][u]});
        }

        for (int v = 0; v < V; ++v) {
            if (graph[u][v] != 0 && !inMST[v] && graph[u][v] < key[v]) {
                parent[v] = u;
                key[v] = graph[u][v];
            }
        }
    }

    return mst;
}

template<typename Func, typename... Args>
double measureTime(Func func, Args&&... args) {
    auto start = chrono::high_resolution_clock::now();
    func(forward<Args>(args)...);
    auto end = chrono::high_resolution_clock::now();
    chrono::duration<double> duration = end - start;
    return duration.count();
}

int main() {
    vector<int> graphSizes = {8000, 10000, 12500};
    int maxWeight = 100;
    int startNode = 0;
    int k_threads[] = {4, 8};


    for (int size : graphSizes) {
        cout << "----------------------------------------" << endl;
        cout << "Розмір графа: " << size << " вершин" << endl;

        vector<vector<int>> graph = generateGraph(size, maxWeight);

        // Послідовне виконання
        double sequentialTime = measureTime([&]() {
            sequentialPrim(graph, startNode);
        });
        cout << "Час послідовного виконання: " << sequentialTime << " секунд" << endl;

        // Паралельне виконання
        for (int k : k_threads) {
            double parallelTime = measureTime([&]() {
                parallelPrim(graph, startNode, k);
            });
            double speedup = sequentialTime / parallelTime;
            double efficiency = speedup / k;
            cout << "Час паралельного виконання (" << k << " потоків): " << parallelTime << " секунд" << endl;
            cout << "Speedup: " << speedup << endl;
            cout << "Effieciency: " << efficiency << endl;
        }
        cout << "----------------------------------------" << endl;
    }



    return 0;
}

