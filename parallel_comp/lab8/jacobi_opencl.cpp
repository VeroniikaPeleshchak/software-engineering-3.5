#include <iostream>
#include <vector>
#include <random>
#include <chrono>
#include <cmath>
#include <cstring>
#include <string>
#include <iomanip>
#include <thread>

#ifdef __APPLE__
#include <OpenCL/opencl.h>
#else
#include <CL/cl.h>
#endif

using clockt = std::chrono::high_resolution_clock;
using namespace std;

//OpenCL kernel
static const char* KERNEL_JACOBI = R"CLC(
__kernel void jacobi_step(
    const int N,
    __global const float* A,
    __global const float* b,
    __global const float* x,
    __global float* x_new)
{
    int i = get_global_id(0);
    if (i >= N) return;

    float s = 0.0f;
    int base = i * N;
    float Aii = A[base + i];

    for (int j = 0; j < N; ++j) {
        if (j != i)
            s += A[base + j] * x[j];
    }
    x_new[i] = (b[i] - s) / Aii;
}
)CLC";


static void check(cl_int err, const char* where) {
    if (err != CL_SUCCESS) {
        cerr << "[OpenCL error] " << where << " -> " << err << "\n";
        exit(1);
    }
}

static vector<float> genDiagDomA(int N) {
    vector<float> A((size_t)N * N);
    mt19937 rng(12345);
    uniform_real_distribution<float> dist(-1.0f, 1.0f);
    for (int i = 0; i < N; ++i) {
        float sum = 0;
        for (int j = 0; j < N; ++j) {
            if (i == j) continue;
            A[i * N + j] = dist(rng);
            sum += fabs(A[i * N + j]);
        }
        A[i * N + i] = sum + 1.0f; //діагональна домінантність
    }
    return A;
}

static vector<float> genVec(int N) {
    vector<float> b(N);
    mt19937 rng(54321);
    uniform_real_distribution<float> dist(-1.0f, 1.0f);
    for (auto &v : b) v = dist(rng);
    return b;
}

static float residual(const vector<float>& A, const vector<float>& x, const vector<float>& b, int N) {
    float rmax = 0.0f;
    for (int i = 0; i < N; ++i) {
        float s = 0.0f;
        for (int j = 0; j < N; ++j)
            s += A[i * N + j] * x[j];
        rmax = max(rmax, fabs(b[i] - s));
    }
    return rmax;
}

//sequential jacobi
static vector<float> jacobiCPU(const vector<float>& A, const vector<float>& b, int N, int max_iter) {
    vector<float> x(N, 0.0f), x_new(N, 0.0f);
    for (int it = 0; it < max_iter; ++it) {
        for (int i = 0; i < N; ++i) {
            float s = 0.0f;
            for (int j = 0; j < N; ++j)
                if (j != i)
                    s += A[i * N + j] * x[j];
            x_new[i] = (b[i] - s) / A[i * N + i];
        }
        x.swap(x_new);
    }
    return x;
}

//parallel jacobi
static vector<float> jacobiCPU_parallel(const vector<float>& A, const vector<float>& b, int N, int max_iter, int threads) {
    vector<float> x(N, 0.0f), x_new(N, 0.0f);
    for (int it = 0; it < max_iter; ++it) {
        auto worker = [&](int start, int end) {
            for (int i = start; i < end; ++i) {
                float s = 0.0f;
                for (int j = 0; j < N; ++j)
                    if (j != i)
                        s += A[i * N + j] * x[j];
                x_new[i] = (b[i] - s) / A[i * N + i];
            }
        };

        vector<thread> pool;
        int chunk = N / threads;
        for (int t = 0; t < threads; ++t) {
            int start = t * chunk;
            int end = (t == threads - 1) ? N : start + chunk;
            pool.emplace_back(worker, start, end);
        }
        for (auto &th : pool) th.join();
        x.swap(x_new);
    }
    return x;
}


int main() {
    vector<int> sizes = {765, 1500, 2450};
    int max_iter = 3500;
    int cpu_threads = 8;


    cout << "========================================================\n\n";

    //initialize OpenCL
    cl_int err;
    cl_uint numPlatforms;
    check(clGetPlatformIDs(0, nullptr, &numPlatforms), "clGetPlatformIDs(count)");
    vector<cl_platform_id> plats(numPlatforms);
    check(clGetPlatformIDs(numPlatforms, plats.data(), nullptr), "clGetPlatformIDs(list)");

    cl_device_id device = nullptr;
    for (auto p : plats) {
        cl_uint n = 0;
        if (clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, 0, nullptr, &n) == CL_SUCCESS && n > 0) {
            vector<cl_device_id> devs(n);
            clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, n, devs.data(), nullptr);
            device = devs[0];
            break;
        }
    }
    if (!device)
        clGetDeviceIDs(plats[0], CL_DEVICE_TYPE_CPU, 1, &device, nullptr);

    cl_context ctx = clCreateContext(nullptr, 1, &device, nullptr, nullptr, &err);
    check(err, "clCreateContext");
    cl_command_queue queue = clCreateCommandQueue(ctx, device, 0, &err);
    check(err, "clCreateCommandQueue");

    const char* src = KERNEL_JACOBI;
    size_t srcLen = strlen(src);
    cl_program prog = clCreateProgramWithSource(ctx, 1, &src, &srcLen, &err);
    check(err, "clCreateProgramWithSource");
    const char* opts = "-cl-fast-relaxed-math";
    err = clBuildProgram(prog, 1, &device, opts, nullptr, nullptr);
    if (err != CL_SUCCESS) {
        size_t logSize = 0;
        clGetProgramBuildInfo(prog, device, CL_PROGRAM_BUILD_LOG, 0, nullptr, &logSize);
        string log(logSize, '\0');
        clGetProgramBuildInfo(prog, device, CL_PROGRAM_BUILD_LOG, logSize, log.data(), nullptr);
        cerr << "Build log:\n" << log << "\n";
        return 1;
    }
    cl_kernel kern = clCreateKernel(prog, "jacobi_step", &err);
    check(err, "clCreateKernel");


    for (int N : sizes) {
        cout << "++++++++++++++ Matrix N = " << N << " ++++++++++++++\n";

        auto A = genDiagDomA(N);
        auto b = genVec(N);

        //sequential
        auto t0 = clockt::now();
        auto x_seq = jacobiCPU(A, b, N, max_iter);
        auto t1 = clockt::now();
        double cpu_seq_ms = chrono::duration<double, milli>(t1 - t0).count();
        float res_seq = residual(A, x_seq, b, N);

        //parallel
        t0 = clockt::now();
        auto x_par = jacobiCPU_parallel(A, b, N, max_iter, cpu_threads);
        t1 = clockt::now();
        double cpu_par_ms = chrono::duration<double, milli>(t1 - t0).count();
        float res_par = residual(A, x_par, b, N);

        //OpenCL
        size_t bytesA = (size_t)N * N * sizeof(float);
        size_t bytesV = (size_t)N * sizeof(float);
        cl_mem dA = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, bytesA, (void*)A.data(), &err);
        cl_mem db = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, bytesV, (void*)b.data(), &err);
        vector<float> x(N, 0.0f), x_new(N, 0.0f);
        cl_mem dx = clCreateBuffer(ctx, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, bytesV, (void*)x.data(), &err);
        cl_mem dx_new = clCreateBuffer(ctx, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR, bytesV, (void*)x_new.data(), &err);

        check(clSetKernelArg(kern, 0, sizeof(int), &N), "set N");
        check(clSetKernelArg(kern, 1, sizeof(cl_mem), &dA), "set A");
        check(clSetKernelArg(kern, 2, sizeof(cl_mem), &db), "set b");

        size_t local = 64;
        size_t global = ((size_t)N + local - 1) / local * local;

        auto g0 = clockt::now();
        for (int it = 0; it < max_iter; ++it) {
            check(clSetKernelArg(kern, 3, sizeof(cl_mem), &dx), "set x");
            check(clSetKernelArg(kern, 4, sizeof(cl_mem), &dx_new), "set x_new");
            check(clEnqueueNDRangeKernel(queue, kern, 1, nullptr, &global, &local, 0, nullptr, nullptr), "enqueue");
            swap(dx, dx_new);
        }
        clFinish(queue);
        auto g1 = clockt::now();

        vector<float> x_gpu(N);
        check(clEnqueueReadBuffer(queue, dx, CL_TRUE, 0, bytesV, x_gpu.data(), 0, nullptr, nullptr), "read x");
        auto g2 = clockt::now();

//      double gpu_kernel_ms = chrono::duration<double, milli>(g1 - g0).count();
        double gpu_total_ms = chrono::duration<double, milli>(g2 - g0).count();
        float res_gpu = residual(A, x_gpu, b, N);

        //metrics
        double flops_per_it = 2.0 * N * (N - 1);
        double gflops = (flops_per_it * max_iter) / (gpu_total_ms * 1e6);

        //speedups
        double sp_seq_par = cpu_seq_ms / cpu_par_ms;
        double sp_seq_gpu = cpu_seq_ms / gpu_total_ms;
        double sp_par_gpu = cpu_par_ms / gpu_total_ms;

        //efficiencies
        double eff_par = sp_seq_par / cpu_threads;


        cout << fixed << setprecision(4);
        cout << "CPU sequential time:  " << cpu_seq_ms << " ms\n";
        cout << "CPU parallel time:    " << cpu_par_ms << " ms\n";
        cout << "OpenCL GPU time:      " << gpu_total_ms << " ms\n";
        cout << "----------------------------------------------\n";
        cout << "Speedup (seq → par):  " << sp_seq_par << "\n";
        cout << "Speedup (seq → GPU):  " << sp_seq_gpu << "\n";
        cout << "Speedup (par → GPU):  " << sp_par_gpu << "\n";
        cout << "----------------------------------------------\n";
        cout << "Efficiency (CPU par): " << eff_par << "\n";
        cout << "GPU Throughput:       " << gflops << " GFLOP/s\n\n";

        cout << "Residuals: seq=" << res_seq
                  << ", par=" << res_par
                  << ", gpu=" << res_gpu << "\n\n";



    }

    cout << "========================================================\n";

}


