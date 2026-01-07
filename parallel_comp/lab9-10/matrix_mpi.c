#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

/**
 * Заповнення матриці випадковими числами (0..99)
 * матриця зберігається в 1D масиві row-major: mat[row * cols + col]
 */
void fillMatrix(int* mat, int rows, int cols) {
    for (int i = 0; i < rows * cols; i++) {
        mat[i] = rand() % 100;
    }
}

void printMatrix(const char* name, int* mat, int rows, int cols) {
    printf("%s:\n", name);
    for (int i = 0; i < rows; i++) {
        for (int j = 0; j < cols; j++) {
            printf("%d\t", mat[i * cols + j]);
        }
        printf("\n");
    }
    printf("\n");
}

/**
 * Послідовне множення: C = A * B
 * A: n×m, B: m×p, C: n×p
 */
void multiplySequential(int* A, int* B, int* C, int n, int m, int p) {
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < p; j++) {
            int sum = 0;
            for (int k = 0; k < m; k++) {
                sum += A[i * m + k] * B[k * p + j];
            }
            C[i * p + j] = sum;
        }
    }
}

int main(int argc, char** argv) {
    MPI_Init(&argc, &argv);

    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);   // номер процесу
    MPI_Comm_size(MPI_COMM_WORLD, &size);   // загальна кількість процесів

    int n = 1000, m = 1000, p = 1000; // розміри за замовчуванням


    if (rank == 0 && argc >= 4) {
        n = atoi(argv[1]);
        m = atoi(argv[2]);
        p = atoi(argv[3]);
    }

    MPI_Bcast(&n, 1, MPI_INT, 0, MPI_COMM_WORLD);
    MPI_Bcast(&m, 1, MPI_INT, 0, MPI_COMM_WORLD);
    MPI_Bcast(&p, 1, MPI_INT, 0, MPI_COMM_WORLD);

    int* A = NULL;
    int* B = NULL;
    int* C = NULL;
    int* Cseq = NULL;

    if (rank == 0) {
        A = (int*)malloc(n * m * sizeof(int));
        B = (int*)malloc(m * p * sizeof(int));
        C = (int*)malloc(n * p * sizeof(int));
        Cseq = (int*)malloc(n * p * sizeof(int));

        srand((unsigned)time(NULL));

        fillMatrix(A, n, m);
        fillMatrix(B, m, p);

        double t1_start = MPI_Wtime();
        multiplySequential(A, B, Cseq, n, m, p);
        double t1_end = MPI_Wtime();
        double seqTime = (t1_end - t1_start) * 1000.0; // мс

        printf("Sequential time: %.3f ms\n", seqTime);


    } else {
        B = (int*)malloc(m * p * sizeof(int));
    }

    MPI_Bcast(B, m * p, MPI_INT, 0, MPI_COMM_WORLD);

    // Розподіл рядків A між процесами
    int rowsPerProc = n / size;
    int remainder   = n % size;


    int myRows;
    if (rank < remainder) {
        myRows = rowsPerProc + 1;
    } else {
        myRows = rowsPerProc;
    }

    int* localA = (myRows > 0) ? (int*)malloc(myRows * m * sizeof(int)) : NULL;
    int* localC = (myRows > 0) ? (int*)malloc(myRows * p * sizeof(int)) : NULL;

    int* sendCounts = NULL;
    int* displs     = NULL;
    int* recvCounts = NULL;
    int* recvDispls = NULL;

    if (rank == 0) {
        sendCounts = (int*)malloc(size * sizeof(int));
        displs     = (int*)malloc(size * sizeof(int));
        recvCounts = (int*)malloc(size * sizeof(int));
        recvDispls = (int*)malloc(size * sizeof(int));

        int offsetA = 0;
        int offsetC = 0;
        for (int i = 0; i < size; i++) {
            int rows_i = rowsPerProc + (i < remainder ? 1 : 0);
            sendCounts[i] = rows_i * m;
            displs[i]     = offsetA;
            offsetA      += sendCounts[i];

            recvCounts[i] = rows_i * p;
            recvDispls[i] = offsetC;
            offsetC      += recvCounts[i];
        }
    }

    MPI_Scatterv(
        A,
        sendCounts,
        displs,
        MPI_INT,
        localA,
        myRows * m,
        MPI_INT,
        0,
        MPI_COMM_WORLD
    );

    //Паралельне множення (MPI)
    MPI_Barrier(MPI_COMM_WORLD);
    double t_par_start = MPI_Wtime();

    for (int i = 0; i < myRows; i++) {
        for (int j = 0; j < p; j++) {
            int sum = 0;
            for (int kq = 0; k < m; k++) {
                sum += localA[i * m + k] * B[k * p + j];
            }
            localC[i * p + j] = sum;
        }
    }

    MPI_Barrier(MPI_COMM_WORLD);
    double t_par_end = MPI_Wtime();
    double localParTime = (t_par_end - t_par_start) * 1000.0; // мс

    double parTime;
    MPI_Reduce(&localParTime, &parTime, 1, MPI_DOUBLE, MPI_MAX, 0, MPI_COMM_WORLD);

    MPI_Gatherv(
        localC,
        myRows * p,
        MPI_INT,
        C,
        recvCounts,
        recvDispls,
        MPI_INT,
        0,
        MPI_COMM_WORLD
    );

    if (rank == 0) {
        printf("Parallel (MPI) time: %.3f ms\n", parTime);


        double t1 = MPI_Wtime();
        multiplySequential(A, B, Cseq, n, m, p);
        double t2 = MPI_Wtime();
        double seqTime2 = (t2 - t1) * 1000.0;
        printf("Sequential (re-measured) time: %.3f ms\n", seqTime2);

        double speedup    = seqTime2 / parTime;
        double efficiency = speedup / size;

        printf("Speedup: %.3f\n", speedup);
        printf("Efficiency: %.3f\n", efficiency);


        int correct = 1;
        for (int i = 0; i < n * p; i++) {
            if (C[i] != Cseq[i]) {
                correct = 0;
                break;
            }
        }
        if (correct) {
            printf("Result check: OK\n");
        } else {
            printf("Result check: ERROR\n");
        }
    }

    //Прибирання
    if (localA) free(localA);
    if (localC) free(localC);
    if (B)      free(B);

    if (rank == 0) {
        if (A)    free(A);
        if (C)    free(C);
        if (Cseq) free(Cseq);
        if (sendCounts) free(sendCounts);
        if (displs)     free(displs);
        if (recvCounts) free(recvCounts);
        if (recvDispls) free(recvDispls);
    }

    MPI_Finalize();
    return 0;
}
