public class Student {
    private int id;
    private String name;
    private int age;
    private String address;
    private String gender;
    private boolean isEnrolled;
    private String phoneNumber;

    public Student(int id, String name, int age, String address, String gender, boolean isEnrolled, String phoneNumber) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.address = address;
        this.gender = gender;
        this.isEnrolled = isEnrolled;
        this.phoneNumber = phoneNumber;
    }
    public String getGender() {return gender;}
    public boolean isEnrolled() {return isEnrolled;}
    public String getName() {return name;}
    public int getId() {return id;}

    @Override
    public String toString() {
        return "Student{id=" + id + ", name='" + name + "', gender='" + gender + "'}";
    }
}