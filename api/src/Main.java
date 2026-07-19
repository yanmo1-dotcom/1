public class Main {
    public static void main(String[] args) {
        System.out.println("=== 测试 1: 男生专用 Token ===");
        System.out.println(ClassApiService.getStudentsApi("token_male_only"));
        
        System.out.println("\n=== 测试 2: 女生专用 Token ===");
        System.out.println(ClassApiService.getStudentsApi("token_female_only"));
        
        System.out.println("\n=== 测试 3: 管理员 Token (看所有人) ===");
        System.out.println(ClassApiService.getStudentsApi("token_leader_admin"));
        
        System.out.println("\n=== 测试 4: 无效 Token ===");
        System.out.println(ClassApiService.getStudentsApi("hacker_token"));
    }
}