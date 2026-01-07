#include <iostream>
#include <vector>
#include <thread>
#include <chrono>
#include <random>

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


//void printMatrix(const vector<vector<int>>& mat){
//    cout << "\n";
//    for(const auto& row : mat){
//        for(int val : row)
//            cout << val << "\t";
//        cout << endl;
//    }
//}

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

void inputMatrixParam(int &n, int &m, int &k){
    n = inputInt("\nEnter a number of rows: ");
    m = inputInt("Enter a number of columns: ");
    k = inputInt("Enter a number of threads: ");
}


void addMatrices(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int n, int m){
    for(int i = 0; i < n; ++i){
        for(int j = 0; j < m; ++j){
            C[i][j] = A[i][j] + B[i][j];
        }
    }
}


void subtractMatrices(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int n, int m){
    for(int i = 0; i < n; ++i){
        for(int j = 0; j < m; ++j){
            C[i][j] = A[i][j] - B[i][j];
        }
    }
}


void addMatricesParallel(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int startRow, int endRow, int m){
    for(int i = startRow; i < endRow; ++i){
        for(int j = 0; j < m; ++j){
            C[i][j] = A[i][j] + B[i][j];
        }
    }
}

void subtractMatricesParallel(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int startRow, int endRow, int m){
    for(int i = startRow; i < endRow; ++i){
        for(int j = 0; j < m; ++j){
            C[i][j] = A[i][j] - B[i][j];
        }
    }
}


template<typename Func, typename ... Args>
void parallelOperation(int n, int k, int m, Func f, Args&&... args){
    vector<thread> threads;
    int rowsPerThread = n / k;
    int remainder = n % k;
    int currentRow = 0;

    for(int i = 0; i < k; ++i){
        int startRow = currentRow;
        int endRow = startRow + rowsPerThread + (i < remainder ? 1 : 0);
        currentRow = endRow;
        threads.emplace_back(f, args..., startRow, endRow, m);
    }

    for(auto& t : threads) t.join();
}

template<typename Func1, typename Func2>
void measureTime(const vector<vector<int>>& A, const vector<vector<int>>& B, vector<vector<int>>& C, int n, int m, int k, Func1 seqFunc, Func2 parFunc){
    const int repeatsInternal = 1;

    auto start = chrono::high_resolution_clock::now();
    seqFunc(A, B, C, n, m);
    auto end = chrono::high_resolution_clock::now();
    auto seqTime = chrono::duration<double, milli>(end - start).count();


    start = chrono::high_resolution_clock::now();
    for(int r = 0; r < repeatsInternal; r++)
        parallelOperation(n, k, m, parFunc, ref(A), ref(B), ref(C));
    end = chrono::high_resolution_clock::now();
    auto parTime = chrono::duration<double, milli>(end - start).count();

    cout << "\nSequential time: " << seqTime << " ms\n";
    cout << "Parallel time: " << parTime << " ms\n";
    cout << "Speedup: " << double(seqTime) / parTime << endl;
    cout << "Efficiency: " << (double(seqTime) / parTime) / k << endl;


}




int main(){
    try{
        bool running = true;
        int n, m, k;

        inputMatrixParam(n, m, k);

        vector<vector<int>> A(n, vector<int>(m));
        vector<vector<int>> B(n, vector<int>(m));
        vector<vector<int>> C(n, vector<int>(m));

        fillMatrix(A, n, m);
        fillMatrix(B, n, m);

        while(running) {
            cout << "\n\n1. Addition (A + B)\n";
            cout << "2. Subtraction (A - B)\n";
            cout << "0. Exit\n";

            cout << "Choose option: ";
            int choice;
            cin >> choice;

            switch (choice) {
                case 1: {
                    measureTime(A, B, C, n, m, k, addMatrices, addMatricesParallel);

                    break;
                }

                case 2: {
                    measureTime(A, B, C, n, m, k, subtractMatrices, subtractMatricesParallel);

                    break;
                }

                case 0: {
                    running = false;
                    break;
                }
                default:
                    cout << "\nInvalid option";

            }
        }

    }
    catch (const exception& e){
        cerr << "Error: " << e.what() << endl;
        return 1;
    }

    return 0;

}


