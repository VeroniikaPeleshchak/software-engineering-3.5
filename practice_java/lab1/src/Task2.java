import java.util.Scanner;

public class Task2 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String input;
        while (true) {
            System.out.println("Enter words separated by comma: ");
            input = sc.nextLine();
            if (input.trim().isEmpty()) {
                System.out.println("Empty input. Try again");
                continue;
            }

            String[] parts = input.split(",");
            boolean hasWord = false;
            for (String part : parts) {
                if (part.trim().matches(".*[a-zA-Zа-яА-Я]+.*")) {
                    hasWord = true;
                    break;
                }
            }

            if (!hasWord) {
                System.out.println("Input must contain words. Try again");
                continue;
            }
            break;
        }

        String reversed = reverseCommaSeparatedWords(input);
        System.out.println("Reversed words: " + reversed);

        sc.close();
    }

    public static String reverseCommaSeparatedWords(String input){
        String[] parts = input.split(",");
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            String word = part.trim();
            if (word.isEmpty()) continue;
            if (!first) result.append(", ");
            result.append(new StringBuilder(word).reverse());
            first = false;
        }
        return result.toString();
    }
}
