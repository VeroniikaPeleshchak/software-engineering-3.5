cmake_minimum_required(VERSION 3.16)
project(matrix_mpi C)

set(CMAKE_OSX_ARCHITECTURES "arm64")
set(CMAKE_C_STANDARD 11)

set(CMAKE_C_COMPILER "/opt/homebrew/bin/mpicc")

add_executable(matrix_mpi matrix_mpi.c)


