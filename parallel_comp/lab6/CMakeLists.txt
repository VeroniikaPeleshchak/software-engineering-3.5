cmake_minimum_required(VERSION 3.26)
project(lab6)

set(CMAKE_CXX_STANDARD 17)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fopenmp")

set(CMAKE_OSX_ARCHITECTURES "arm64")

add_executable(lab6 main.cpp)



