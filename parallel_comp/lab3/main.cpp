#include <iostream>
#include <vector>
#include <random>
#include <thread>
#include <chrono>
#include <cmath>
#include <iomanip>
#include <algorithm>

using namespace std;
using clockt = chrono::high_resolution_clock;

// Генерація строго діагонально-домінантної матриці
vector<vector<double>> generate_diagonally_dominant_matrix(int n, double scale = 10.0) {
    vector<vector<double>> A(n, vector<double>(n));
    random_device rd;
    mt19937_64 gen(rd());
    uniform_real_distribution<double> dist(-scale, scale);

    for (int i = 0; i < n; ++i) {
        double row_sum = 0.0;
        for (int j = 0; j < n; ++j) {
            if (i == j) continue;
            A[i][j] = dist(gen);
            row_sum += fabs(A[i][j]);
        }
        A[i][i] = row_sum + fabs(dist(gen)) + 1.0;
    }
    return A;
}

// Генерація вектора b
vector<double> generate_vector(int n, double scale = 10.0) {
    vector<double> b(n);
    random_device rd;
    mt19937_64 gen(rd());
    uniform_real_distribution<double> dist(-scale, scale);
    for (int i = 0; i < n; ++i) b[i] = dist(gen);
    return b;
}

struct SolverResult {
    vector<double> x;
    int iterations;
    double solve_time_ms;
};

// Послідовний метод Якобі
SolverResult jacobi_sequential(const vector<vector<double>>& A, const vector<double>& b, int max_iter = 10000) {
    int n = (int)A.size();
    vector<double> x(n, 0.0), x_new(n, 0.0);
    auto t0 = clockt::now();
    int it = 0;
    for (; it < max_iter; ++it) {
        for (int i = 0; i < n; ++i) {
            double s = 0.0;
            for (int j = 0; j < n; ++j) if (j != i) s += A[i][j] * x[j];
            x_new[i] = (b[i] - s) / A[i][i];
        }
        x.swap(x_new);
    }
    auto t1 = clockt::now();
    double ms = chrono::duration<double, milli>(t1 - t0).count();
    return {x, it, ms};
}

// Паралельний метод Якобі (std::thread)
SolverResult jacobi_parallel(const vector<vector<double>>& A, const vector<double>& b, int num_threads, int max_iter = 10000) {
    int n = (int)A.size();
    vector<double> x(n, 0.0), x_new(n, 0.0);

    auto t0 = clockt::now();
    int it = 0;

    for (; it < max_iter; ++it) {
        auto worker = [&](int start, int end) {
            for (int i = start; i < end; ++i) {
                double s = 0.0;
                for (int j = 0; j < n; ++j) if (j != i) s += A[i][j] * x[j];
                x_new[i] = (b[i] - s) / A[i][i];
            }
        };

        vector<thread> threads;
        int chunk = (n + num_threads - 1) / num_threads;
        for (int t = 0; t < num_threads; ++t) {
            int start = t * chunk;
            int end = min(start + chunk, n);
            if (start < n)
                threads.emplace_back(worker, start, end);
        }
        for (auto &th : threads) th.join();

        x.swap(x_new);
    }

    auto t1 = clockt::now();
    double ms = chrono::duration<double, milli>(t1 - t0).count();
    return {x, it, ms};
}

int main(int argc, char** argv) {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    int n = 5000;
    int threads = 6;
    int max_iter = 10000;

    if (argc >= 2) n = stoi(argv[1]);
    if (argc >= 3) threads = stoi(argv[2]);
    if (argc >= 4) max_iter = stoi(argv[3]);

    cout << "\n";
    cout << "n = " << n << ", threads = " << threads << ", max_iter = " << max_iter << "\n\n";

    auto A = generate_diagonally_dominant_matrix(n);
    auto b = generate_vector(n);

    cout << "Running sequential Jacobi...\n";
    auto seq = jacobi_sequential(A, b, max_iter);
    cout << "Time: " << seq.solve_time_ms << " ms\n";
    cout << "Iterations: " << seq.iterations << "\n\n";

    cout << "Running parallel Jacobi (" << threads << " threads)...\n";
    auto par = jacobi_parallel(A, b, threads, max_iter);
    cout << "Time: " << par.solve_time_ms << " ms\n";
    cout << "Iterations: " << par.iterations << "\n\n";

    // Обчислюємо прискорення і ефективність
    double speedup = seq.solve_time_ms / par.solve_time_ms;
    double efficiency = speedup / threads;

    cout << fixed << setprecision(4);
    cout << "Speedup (S) = " << speedup << "\n";
    cout << "Efficiency (E) = " << efficiency << "\n\n";

    cout << "x[0..4] = ";
    for (int i = 0; i < min(5, n); ++i) cout << par.x[i] << (i+1 < min(5, n) ? ", " : "\n");

    return 0;
}

