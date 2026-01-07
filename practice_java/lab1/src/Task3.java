import java.util.*;

abstract class Person{
    private String name;
    private int age;
    private String gender;

    public Person(String name, int age, String gender){
        this.name = name;
        this.age = age;
        this.gender = gender;
    }

    public String getName(){ return name; }
    public int getAge(){ return age; }
    public String getGender(){ return gender; }

    @Override
    public String toString(){
        return "\nName: " + name + "\nAge: " + age + "\nGender: " + gender;
    }
}

class Teacher extends Person{
    private String position;
    private int experience;

    public Teacher(String name, int age, String gender, String position,  int experience){
        super(name, age, gender);
        this.position = position;
        this.experience = experience;
    }

    public int getExperience(){ return experience; }

    @Override
    public String toString(){
        return "\n\t\tTeacher: " + super.toString() + "\nPosition: " + position + ", Experience: " + experience;
    }


}

class Student extends Person{
    private String faculty;
    private double gpa;

    public Student(String name, int age, String gender, String faculty, double gpa) {
        super(name, age, gender);
        this.faculty = faculty;
        this.gpa = gpa;
    }

    public double getGpa(){ return gpa; }

    @Override
    public String toString(){
        return "\n\t\tStudent: " + super.toString() + "\nFaculty: " + faculty + ", GPA: " + gpa;
    }

}



public class Task3 {
    private static final Scanner sc  = new Scanner(System.in);

    public static void main(String[] args) {
        List<Person> people = new ArrayList<>();

        people.add(new Teacher("Ivan Petrenko", 45, "M", "Professor", 20));
        people.add(new Teacher("Olena Shevchenko", 38, "F", "Assistant", 10));
        people.add(new Student("Andriy Kovalenko", 20, "M", "Computer Science", 93.5));
        people.add(new Student("Kateryna Bondar", 19, "F", "Mathematics", 97.2));
        people.add(new Student("Oleh Sydorenko", 21, "M", "Physics", 88.9));

        while(true){
            printMenu();
            int choice = readInt("Enter your choice: ");

            switch(choice){
                case 1 -> showAllPeople(people);
                case 2 -> sortByName(people);
                case 3 -> findMinTeacher(people);
                case 4 -> findTopStudent(people);
                case 5 -> addPerson(people);
                case 0 -> {
                    System.out.println("Exiting...");
                    return;
                }
                default -> System.out.println("\n\tInvalid choice\n");
            }
        }
    }

    private static void printMenu(){
        System.out.println("\n---MENU---");
        System.out.println("1. Show all people");
        System.out.println("2. Sort by name");
        System.out.println("3. Teacher with minimum experience");
        System.out.println("4. Students with maximum success");
        System.out.println("5. Add person");
        System.out.println("0. Exit");
    }

    private static int readInt(String message){
        while(true){
            System.out.println(message);
            try{
                return Integer.parseInt(sc.nextLine());
            } catch(NumberFormatException e){
                System.out.println("\n\tInvalid input\n");
            }
        }
    }

    private static double readDouble(String message){
        while(true){
            System.out.println(message);
            try {
                return Double.parseDouble(sc.nextLine());
            } catch (NumberFormatException e){
                System.out.println("\n\tInvalid input\n");
            }
        }
    }


    private static void showAllPeople(List<Person> people){
        if(people.isEmpty()){
            System.out.println("\n\tNo people found\n");
        } else {
            for(Person person : people) System.out.println("  "  + person);
        }
    }

    private static void sortByName(List<Person> people){
        people.sort(Comparator.comparing(Person::getName, String.CASE_INSENSITIVE_ORDER));
        System.out.println("\n\tSorted by name\n");
    }

    private static void findMinTeacher(List<Person> people){
        Teacher min = null;
        for(Person p : people){
            if(p instanceof Teacher t){
                if(min == null || t.getExperience() < min.getExperience()) min = t;
            }
        }

        if(min != null){
            System.out.println("\nTeacher with minimum experience:");
            System.out.println("  " + min);
        } else {
            System.out.println("\n\tNo teacher found\n");
        }
    }

    private static void findTopStudent(List<Person> people){
        double max = -1;
        for(Person p : people){
            if(p instanceof Student s){
                if(s.getGpa() > max) max = s.getGpa();
            }
        }

        if(max < 0){
            System.out.println("\n\tNo students found\n");
            return;
        }

        System.out.println("\nStudents with maximum GPA (" + max + "):");
        for(Person p : people){
            if(p instanceof Student s && Math.abs(s.getGpa() - max) < 1e-9){
                System.out.println("  " + s);
            }
        }
    }


    private static void addPerson(List<Person> people){
        String type;
        while(true){
            System.out.print("\nEnter person's type (s/t): ");
            type = sc.nextLine().trim().toLowerCase();
            if(type.equals("s") || type.equals("t")) break;
            System.out.println("\n\tEnter only 's' or 't'");
        }

        System.out.print("\nEnter person's name: ");
        String name = sc.nextLine();
        int age = readAge(type);
        String gender = readGender();


        if(type.equals("t")) {
            String position = readPosition();
            int experience = readExperience(age);
            people.add(new Teacher(name, age, gender, position, experience));
        } else  {
            String faculty = readFaculty();
            double gpa = readGpa();
            people.add(new Student(name, age, gender, faculty, gpa));
        }

        System.out.println("\n\tPerson added successfully\n");
    }

    private static int readAge(String type) {
        int min = type.equals("t") ? 25 : 16;
        int max = 100;
        while (true) {
            int age = readInt("\nEnter person's age (" + min + "-" + max + "): ");
            if (age >= min && age <= max) return age;
            System.out.println("\n\tInvalid age range\n");
        }
    }

    private static String readGender() {
        while (true) {
            System.out.print("\nEnter person's gender (M/F): ");
            String g = sc.nextLine().trim().toUpperCase();
            if (g.equals("M") || g.equals("F")) return g;
            System.out.println("\n\tEnter only M or F\n");
        }
    }

    private static int readExperience(int age) {
        while (true) {
            int exp = readInt("\nEnter teacher's experience (years): ");
            if (exp >= 0 && exp <= age - 25) return exp;
            System.out.println("\n\tInvalid experience for given age\n");
        }
    }

    private static String readPosition() {
        String[] positions = {"Professor", "Assistant", "Lecturer", "Senior Lecturer"};
        while (true) {
            System.out.println("\nChoose position:");
            for (int i = 0; i < positions.length; i++) {
                System.out.println((i + 1) + ". " + positions[i]);
            }
            int choice = readInt("\nEnter choice (1-" + positions.length + "): ");
            if (choice >= 1 && choice <= positions.length) return positions[choice - 1];
            System.out.println("\n\tInvalid choice\n");
        }
    }

    private static String readFaculty() {
        String[] faculties = {"Computer Science", "Mathematics", "Physics", "Economics", "History"};
        while (true) {
            System.out.println("\nChoose faculty:");
            for (int i = 0; i < faculties.length; i++) {
                System.out.println((i + 1) + ". " + faculties[i]);
            }
            int choice = readInt("\nEnter choice (1-" + faculties.length + "): ");
            if (choice >= 1 && choice <= faculties.length) return faculties[choice - 1];
            System.out.println("\n\tInvalid choice\n");
        }
    }

    private static double readGpa() {
        while (true) {
            double gpa = readDouble("\nEnter student's GPA (51-100): ");
            if (gpa >= 51 && gpa <= 100) return gpa;
            System.out.println("\n\tInvalid GPA range\n");
        }
    }


}
