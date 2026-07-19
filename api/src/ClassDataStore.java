import java.util.*;

public class ClassDataStore {

    // 1. 学生数据库 (这个没问题)
    public static final List<Student> STUDENT_DB = Arrays.asList(
        new Student(1, "张三", 20, "北京市海淀区中关村大街1号", "male", true, "13800001111"),
        new Student(2, "李四", 19, "上海市浦东新区世纪大道100号", "female", false, "13800002222"),
        new Student(3, "王五", 21, "广州市天河区体育西路50号", "male", false, "13800003333"),
        new Student(4, "赵六", 20, "深圳市南山区科技园路88号", "female", true, "13800004444"),
        new Student(5, "孙七", 22, "杭州市西湖区文三路20号", "male", false, "13800005555"),
        new Student(6, "周八", 19, "南京市鼓楼区中央路200号", "female", true, "13800006666")
    );

    // 2. 【修改点】Token 映射表：Value 应该是 String (性别)，而不是 Student
    // 如果 Value 是 null，代表不限制性别（即管理员/班干部权限）
    public static final Map<String, String> TOKEN_MAP = new HashMap<>();

    static {
        TOKEN_MAP.put("token_male_only", "male");       // 只能看男生
        TOKEN_MAP.put("token_female_only", "female");   // 只能看女生
        TOKEN_MAP.put("token_leader_admin", null);      // 【修改点】这里不要加引号，直接传 null
    }

    // 3. 【修改点】修正拼写错误 chenck -> check, INVALTD -> INVALID
    public static String checkToken(String token) {
        if (token == null || !TOKEN_MAP.containsKey(token)) {
            return "INVALID"; 
        }
        return TOKEN_MAP.get(token);
    }
}