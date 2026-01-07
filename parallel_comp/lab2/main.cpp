#include <iostream>
#include <vector>
#include <thread>
#include <random>
#include <chrono>

using namespace std;

void fillMatrix(vector<vector<int>>& mat, int n, int m){
    random_device rd;
    mt19937 gen(rd());
    uniform_int_distribution<> dis(0, 10000);

    for(int i = 0; i < n; ++i){
        for(int j = 0; j < m; ++j){
            mat[i][j] = dis(gen);
        }
    }
}

void printMatrix(const vector<vector<int>>& mat){
    cout << "\n";
    for(const auto& row : mat){
        for(int val : row)
            cout << val << "\t";
        cout << endl;
    }
}

int inputInt(const string& prompt){
    int x;
    while(true){
        cout << prompt;
        if(cin >> x && x > 0) break;
        else{
            cout << "\nInvalid input\n";
            cin.clear();
            cin.ignore(numeric_limits<streamsize>::max(), '\n');
        }
    }

    return x;
}


void multiplyMatrices(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int n, int m, int p){
    for(int i = 0; i < n; ++i){
        for(int j = 0; j < p; ++j){
            C[i][j] = 0;
            for(int k = 0; k < m; ++k){
                C[i][j] += A[i][k] * B[k][j];
            }
        }
    }
}

void multiplyMatricesParallel(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int startRow, int endRow, int m, int p){
    for(int i = startRow; i < endRow; ++i){
        for(int j = 0; j < p; ++j){
            C[i][j] = 0;
            for(int k = 0; k < m; ++k){
                C[i][j] += A[i][k] * B[k][j];
            }
        }
    }
}


void parallelMultiply(int n, int k, const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int m, int p){
    vector<thread> threads;
    int rowsPerThread = n / k;
    int remainder = n % k;
    int currentRow = 0;

    for(int i = 0; i < k; ++i){
        int startRow = currentRow;
        int endRow = startRow + rowsPerThread + (i < remainder ? 1 : 0);
        currentRow = endRow;
        threads.emplace_back(multiplyMatricesParallel, ref(A), ref(B), ref(C), startRow, endRow, m, p);
    }

    for(auto& t : threads) t.join();
}



int main(){
    try{
        int n, m, k, p;
        n = inputInt("\n\nEnter a number of rows for A: ");
        m = inputInt("Enter a number of columns for A (and B): ");
        p = inputInt("Enter a number of columns for B: ");
        k = inputInt("Enter a number of threads: ");

        vector<vector<int>> A(n, vector<int>(m));
        vector<vector<int>> B(m, vector<int>(p));
        vector<vector<int>> C(n, vector<int>(p));

        fillMatrix(A, n, m);
        fillMatrix(B, m, p);

        auto start = chrono::high_resolution_clock::now();
        multiplyMatrices(A, B, C, n, m, p);
        auto end = chrono::high_resolution_clock::now();
        auto seqTime = chrono::duration<double, milli>(end - start).count();

        start = chrono::high_resolution_clock::now();
        parallelMultiply(n, k, A, B, C, m, p);
        end = chrono::high_resolution_clock::now();
        auto parTime = chrono::duration<double, milli>(end - start).count();

        cout << "\nSequential time: " << seqTime << " ms\n";
        cout << "Parallel time: " << parTime << " ms\n";
        cout << "Speedup: " << double(seqTime) / parTime << endl;
        cout << "Efficiency: " << (double(seqTime) / parTime) / k << endl;



    }
    catch(const exception& e) {
        cerr << "Error: " << e.what() << endl;
        return 1;
    }

    return 0;
}