//Варіант 3
import java.util.Arrays;
import  java.util.Scanner;

public class Task1 {
    public static void main(String[] args) {
       Scanner sc = new Scanner(System.in);

       System.out.println("Enter a number of rows: ");
       int row = readPositiveInt(sc);
       System.out.println("Enter a number of columns: ");
       int col = readPositiveInt(sc);

       int[][] matrix = new int[row][col];

       System.out.println("Enter the elements of the matrix: ");
       for (int i = 0; i < row; i++) {
           for (int j = 0; j < col; j++) {
               matrix[i][j] = readInt(sc);
           }
       }

        if (row <= 0 || col <= 0) {
            System.out.println("\n\tRows and columns must be positive");
            return;
        }

       int[] fibMins = minFibonacciPerRow(matrix);
       System.out.println("Minimum Fibonacci numbers by row: " +  Arrays.toString(fibMins));

       sc.close();
    }

    public static int[] minFibonacciPerRow(int[][] matrix){
        int[] result = new int[matrix.length];
        for(int i = 0; i < matrix.length; i++){
            int min = Integer.MAX_VALUE;
            for (int val : matrix[i]) {
                if (isFibonacci(val) && val < min) min = val;
            }
            result[i] = (min == Integer.MAX_VALUE) ? -1 : min;
        }
        return result;
    }

    private static boolean isFibonacci(int n){
        if(n < 0) return false;
        long nn = n;
        long test1 = 5L * nn * nn + 4;
        long test2 = 5L * nn * nn - 4;
        return isPerfectSquare(test1) || isPerfectSquare(test2);
    }

    private static boolean isPerfectSquare(long x){
        long s = (long)Math.sqrt(x);
        return s * s == x;
    }

    private static int readInt(Scanner sc) {
        while (true) {
            if (sc.hasNextInt()) {
                return sc.nextInt();
            } else {
                System.out.println("Invalid input. Please enter an integer:");
                sc.next();
            }
        }
    }

    private static int readPositiveInt(Scanner sc) {
        int num;
        do {
            num = readInt(sc);
            if (num <= 0) {
                System.out.println("Number must be > 0:");
            }
        } while (num <= 0);
        return num;
    }
}